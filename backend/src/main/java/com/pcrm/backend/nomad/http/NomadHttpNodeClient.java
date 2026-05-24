package com.pcrm.backend.nomad.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.pcrm.backend.nomad.NomadGpuDeviceSnapshot;
import com.pcrm.backend.nomad.NomadNodeClient;
import com.pcrm.backend.nomad.NomadNodeSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class NomadHttpNodeClient implements NomadNodeClient {

    private static final String META_NODE_ID_KEY = "private-cloud-resource-manager.node_id";

    private final RestClient restClient;

    public NomadHttpNodeClient(String nomadBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(nomadBaseUrl).build();
    }

    @Override
    public List<NomadNodeSnapshot> fetchClientNodes() {
        // 1. Fetch the summaries using automatic JSON mapping
        List<NomadNodeSummary> summaries = restClient.get()
                .uri("/v1/nodes")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }

        List<NomadNodeSnapshot> snapshots = new ArrayList<>();

        for (NomadNodeSummary summary : summaries) {
            if (summary.id() == null || summary.id().isBlank()) {
                continue;
            }

            try {
                // 2. Fetch the details required for the Custom Meta Tag & NodeResources
                NomadNodeDetails details = restClient.get()
                        .uri("/v1/node/{id}", summary.id())
                        .retrieve()
                        .body(NomadNodeDetails.class);

                if (details == null) {
                    continue;
                }

                // 3. Enforce identification by the custom meta tag
                String customNodeId = details.meta() != null ? details.meta().get(META_NODE_ID_KEY) : null;
                if (customNodeId == null || customNodeId.isBlank()) {
                    log.warn("Skipping Nomad node {} because Meta.{} is missing", summary.id(), META_NODE_ID_KEY);
                    continue;
                }

                snapshots.add(mapToSnapshot(summary, details, customNodeId));

            } catch (RestClientResponseException ex) {
                log.warn("Skipping Nomad node {} after detail fetch failure: status {}", summary.id(), ex.getStatusCode());
            } catch (IllegalArgumentException ex) {
                log.warn("Skipping Nomad node with invalid UUID {}", summary.id());
            }
        }

        return snapshots;
    }

    private NomadNodeSnapshot mapToSnapshot(NomadNodeSummary summary, NomadNodeDetails details, String customNodeId) {
        String ipAddress = resolveIpAddress(details.httpAddr(), summary.address());

        int cpuCores = details.nodeResources() != null && details.nodeResources().cpu() != null
                ? Optional.ofNullable(details.nodeResources().cpu().totalCpuCores()).orElse(1) : 1;

        int ramMb = details.nodeResources() != null && details.nodeResources().memory() != null
                ? Optional.ofNullable(details.nodeResources().memory().memoryMb()).orElse(1) : 1;

        String dockerVersion = resolveDockerVersion(details.drivers());
        List<NomadGpuDeviceSnapshot> gpuDevices = resolveGpuDevices(details.nodeResources(), details.attributes());

        return new NomadNodeSnapshot(
                customNodeId,
                UUID.fromString(summary.id()),
                Optional.ofNullable(details.name()).orElse(summary.name()),
                ipAddress,
                Optional.ofNullable(details.status()).orElse(summary.status()),
                Optional.ofNullable(details.statusDescription()).orElse(summary.statusDescription()),
                Optional.ofNullable(details.schedulingEligibility()).orElse(summary.schedulingEligibility()),
                Optional.ofNullable(details.datacenter()).orElse(summary.datacenter()),
                Optional.ofNullable(details.nodePool()).orElse(summary.nodePool()),
                Optional.ofNullable(details.nodeClass()).orElse(summary.nodeClass()),
                details.drain() != null ? details.drain() : (summary.drain() != null ? summary.drain() : false),
                Optional.ofNullable(details.attributes() != null ? details.attributes().get("nomad.version") : null).orElse(summary.version()),
                dockerVersion,
                Optional.ofNullable(details.createIndex()).orElse(summary.createIndex()),
                Optional.ofNullable(details.modifyIndex()).orElse(summary.modifyIndex()),
                cpuCores,
                ramMb,
                Optional.ofNullable(summary.version()).orElse(dockerVersion),
                gpuDevices
        );
    }

    private List<NomadGpuDeviceSnapshot> resolveGpuDevices(
            NomadNodeResources nodeResources,
            Map<String, String> nodeAttributes
    ) {
        if (nodeResources == null || nodeResources.devices() == null) {
            return List.of();
        }

        List<NomadGpuDeviceSnapshot> devices = new ArrayList<>();
        for (NomadDeviceResource device : nodeResources.devices()) {
            if (device.vendor() == null || device.type() == null) {
                continue;
            }
            if (!"nvidia".equalsIgnoreCase(device.vendor()) || !"gpu".equalsIgnoreCase(device.type())) {
                continue;
            }

            String model = Optional.ofNullable(device.name()).orElse("NVIDIA GPU");
            Integer memoryMiB = resolveMemoryMiB(device.attributes());
            String driverVersion = nodeAttributes == null ? null : nodeAttributes.get("driver.nvidia.version");
            var instances = device.instances();

            if (instances == null || instances.isEmpty()) {
                devices.add(new NomadGpuDeviceSnapshot(
                        device.vendor().toLowerCase(Locale.ROOT) + ":" + model,
                        device.vendor().toLowerCase(Locale.ROOT),
                        device.type().toLowerCase(Locale.ROOT),
                        model,
                        memoryMiB,
                        null,
                        driverVersion
                ));
                continue;
            }

            for (NomadDeviceInstance instance : instances) {
                devices.add(new NomadGpuDeviceSnapshot(
                        Optional.ofNullable(instance.id()).orElse(model),
                        device.vendor().toLowerCase(Locale.ROOT),
                        device.type().toLowerCase(Locale.ROOT),
                        model,
                        memoryMiB,
                        Boolean.TRUE.equals(instance.healthy()) ? "healthy" : "unhealthy",
                        driverVersion
                ));
            }
        }

        return devices;
    }

    private Integer resolveMemoryMiB(Map<String, JsonNode> attributes) {
        if (attributes == null) {
            return null;
        }

        JsonNode memory = attributes.get("memory");
        if (memory == null || memory.isNull()) {
            memory = attributes.get("device.attr.memory");
        }
        if (memory == null || memory.isNull()) {
            return null;
        }

        if (memory.isNumber()) {
            return memory.asInt();
        }

        JsonNode intValue = memory.get("Int");
        if (intValue != null && intValue.isNumber()) {
            return intValue.asInt();
        }

        JsonNode floatValue = memory.get("Float");
        if (floatValue != null && floatValue.isNumber()) {
            return (int) Math.round(floatValue.asDouble());
        }

        JsonNode stringValue = memory.get("String");
        if (stringValue != null && stringValue.isTextual()) {
            try {
                return Integer.parseInt(stringValue.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private String resolveIpAddress(String httpAddr, String addressFallback) {
        String source = httpAddr != null ? httpAddr : addressFallback;
        if (source == null || source.isBlank()) return null;

        if (!source.contains(":")) return source;

        try {
            URI uri = URI.create("http://" + source);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        } catch (IllegalArgumentException ignored) {
        }

        int separatorIndex = source.lastIndexOf(':');
        return separatorIndex > 0 ? source.substring(0, separatorIndex) : source;
    }

    private String resolveDockerVersion(Map<String, NomadDriver> drivers) {
        if (drivers != null && drivers.containsKey("docker")) {
            NomadDriver docker = drivers.get("docker");
            if (docker.attributes() != null) {
                return docker.attributes().get("driver.docker.version");
            }
        }
        return null;
    }

    // ========================================================================
    // DTO Records mapped directly to Nomad's JSON responses
    // ========================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadNodeSummary(
            @JsonProperty("ID") String id,
            @JsonProperty("Name") String name,
            @JsonProperty("Address") String address,
            @JsonProperty("Datacenter") String datacenter,
            @JsonProperty("NodePool") String nodePool,
            @JsonProperty("NodeClass") String nodeClass,
            @JsonProperty("Version") String version,
            @JsonProperty("Drain") Boolean drain,
            @JsonProperty("SchedulingEligibility") String schedulingEligibility,
            @JsonProperty("Status") String status,
            @JsonProperty("StatusDescription") String statusDescription,
            @JsonProperty("CreateIndex") Long createIndex,
            @JsonProperty("ModifyIndex") Long modifyIndex
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadNodeDetails(
            @JsonProperty("ID") String id,
            @JsonProperty("Name") String name,
            @JsonProperty("HTTPAddr") String httpAddr,
            @JsonProperty("Datacenter") String datacenter,
            @JsonProperty("NodePool") String nodePool,
            @JsonProperty("NodeClass") String nodeClass,
            @JsonProperty("Drain") Boolean drain,
            @JsonProperty("SchedulingEligibility") String schedulingEligibility,
            @JsonProperty("Status") String status,
            @JsonProperty("StatusDescription") String statusDescription,
            @JsonProperty("CreateIndex") Long createIndex,
            @JsonProperty("ModifyIndex") Long modifyIndex,
            @JsonProperty("Meta") Map<String, String> meta,
            @JsonProperty("Attributes") Map<String, String> attributes,
            @JsonProperty("NodeResources") NomadNodeResources nodeResources,
            @JsonProperty("Drivers") Map<String, NomadDriver> drivers
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadDriver(
            @JsonProperty("Attributes") Map<String, String> attributes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadNodeResources(
            @JsonProperty("Cpu") NomadCpuResources cpu,
            @JsonProperty("Memory") NomadMemoryResources memory,
            @JsonProperty("Devices") List<NomadDeviceResource> devices
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadDeviceResource(
            @JsonProperty("Vendor") String vendor,
            @JsonProperty("Type") String type,
            @JsonProperty("Name") String name,
            @JsonProperty("Attributes") Map<String, JsonNode> attributes,
            @JsonProperty("Instances") List<NomadDeviceInstance> instances
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadDeviceInstance(
            @JsonProperty("ID") String id,
            @JsonProperty("Healthy") Boolean healthy
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadCpuResources(
            @JsonProperty("TotalCpuCores") Integer totalCpuCores
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadMemoryResources(
            @JsonProperty("MemoryMB") Integer memoryMb
    ) {
    }
}
