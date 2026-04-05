package com.pcrm.backend.nomad.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pcrm.backend.nomad.NomadNodeClient;
import com.pcrm.backend.nomad.NomadNodeSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class NomadHttpNodeClient implements NomadNodeClient {

    private static final String META_NODE_ID_KEY = "private-cloud-resource-manager.node_id";

    private final RestClient restClient;

    public NomadHttpNodeClient(@Value("${app.nomad.base-url}") String nomadBaseUrl) {
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

        int gpuCount = resolveGpuCount(details.attributes());
        String dockerVersion = resolveDockerVersion(details.drivers());

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
                gpuCount,
                Optional.ofNullable(summary.version()).orElse(dockerVersion)
        );
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

    private int resolveGpuCount(Map<String, String> attributes) {
        if (attributes == null) return 0;

        for (String key : List.of("resources.gpu", "gpu.count", "nvidia.gpu.count")) {
            if (attributes.containsKey(key)) {
                try {
                    return Integer.parseInt(attributes.get(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return attributes.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains("gpu"))
                .mapToInt(e -> {
                    try {
                        return Integer.parseInt(e.getValue());
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .max()
                .orElse(0);
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
            @JsonProperty("Memory") NomadMemoryResources memory
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
