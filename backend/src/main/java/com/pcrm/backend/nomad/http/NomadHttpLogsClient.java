package com.pcrm.backend.nomad.http;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pcrm.backend.nomad.NomadLogsClient;
import com.pcrm.backend.nomad.NomadLogsUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
public class NomadHttpLogsClient implements NomadLogsClient {

    private final RestClient restClient;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final String nomadBaseUrl;

    public NomadHttpLogsClient(String nomadBaseUrl, JsonMapper jsonMapper) {
        this.restClient = RestClient.builder().baseUrl(nomadBaseUrl).build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.jsonMapper = jsonMapper;
        this.nomadBaseUrl = nomadBaseUrl;
    }

    @Override
    public List<NomadAllocationSnapshot> listJobAllocations(String nomadJobId) {
        List<NomadAllocationResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/job/{jobId}/allocations")
                        .queryParam("all", true)
                        .build(nomadJobId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (response == null || response.isEmpty()) {
            return List.of();
        }

        return response.stream()
                .filter(allocation -> allocation.id() != null && !allocation.id().isBlank())
                .map(allocation -> new NomadAllocationSnapshot(
                        allocation.id(),
                        allocation.clientStatus(),
                        Objects.requireNonNullElse(allocation.createIndex(), 0L),
                        Objects.requireNonNullElse(allocation.modifyIndex(), 0L)
                ))
                .toList();
    }

    @Override
    public void streamAllocationLogs(
            String allocationId,
            String streamType,
            boolean follow,
            long offset,
            Consumer<NomadLogFrame> frameConsumer
    ) throws IOException, InterruptedException {
        URI uri = UriComponentsBuilder.fromUriString(nomadBaseUrl)
                .path("/v1/client/fs/logs/{allocId}")
                .queryParam("task", "user-workload")
                .queryParam("type", streamType)
                .queryParam("follow", follow)
                .queryParam("origin", "start")
                .queryParam("offset", offset)
                .queryParam("plain", false)
                .buildAndExpand(allocationId)
                .encode()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(20))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() == 404) {
            throw new NomadLogsUnavailableException("Nomad logs were not found for allocation " + allocationId);
        }
        if (response.statusCode() >= 400) {
            throw new IOException("Nomad log stream failed with status " + response.statusCode());
        }

        try (InputStream inputStream = response.body(); JsonParser parser = jsonMapper.createParser(inputStream)) {
            while (!parser.isClosed()) {
                JsonToken token = parser.nextToken();
                if (token == null) {
                    break;
                }
                if (token != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    continue;
                }

                JsonNode frameNode = jsonMapper.readTree(parser);
                NomadLogFrame frame = decodeFrame(frameNode);
                if (frame != null) {
                    frameConsumer.accept(frame);
                }
            }
        }
    }

    NomadLogFrame decodeFrame(JsonNode root) {
        if (root == null || root.isNull() || root.isObject() && root.isEmpty()) {
            return NomadLogFrame.heartbeat();
        }

        try {
            long offset = resolveOffset(root);
            String encodedData = resolveText(root, "Data", "data");
            if (encodedData != null && !encodedData.isBlank()) {
                byte[] decodedBytes;
                try {
                    decodedBytes = Base64.getDecoder().decode(encodedData);
                } catch (IllegalArgumentException base64Error) {
                    decodedBytes = encodedData.getBytes(StandardCharsets.UTF_8);
                }

                String chunk = new String(decodedBytes, StandardCharsets.UTF_8);
                if (!chunk.isEmpty()) {
                    return NomadLogFrame.chunk(chunk, offset, decodedBytes.length);
                }
            }

            String fileEvent = resolveText(root, "FileEvent", "fileEvent", "event");
            if (fileEvent != null && !fileEvent.isBlank()) {
                return NomadLogFrame.status(fileEvent, offset);
            }

            return NomadLogFrame.heartbeat();
        } catch (Exception parseError) {
            log.debug("Skipping unparsable Nomad log frame node");
            return null;
        }
    }

    private long resolveOffset(JsonNode root) {
        if (root == null) {
            return -1L;
        }
        JsonNode offsetNode = root.has("Offset") ? root.get("Offset") : root.get("offset");
        if (offsetNode == null || offsetNode.isNull()) {
            return -1L;
        }
        if (offsetNode.isNumber()) {
            return offsetNode.asLong();
        }
        if (offsetNode.isTextual()) {
            try {
                return Long.parseLong(offsetNode.asText());
            } catch (NumberFormatException ignored) {
                return -1L;
            }
        }
        return -1L;
    }

    private String resolveText(JsonNode root, String... fieldNames) {
        if (root == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode valueNode = root.get(fieldName);
            if (valueNode != null && valueNode.isTextual()) {
                return valueNode.asText();
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NomadAllocationResponse(
            @JsonProperty("ID") String id,
            @JsonProperty("ClientStatus") String clientStatus,
            @JsonProperty("CreateIndex") Long createIndex,
            @JsonProperty("ModifyIndex") Long modifyIndex
    ) {
    }
}
