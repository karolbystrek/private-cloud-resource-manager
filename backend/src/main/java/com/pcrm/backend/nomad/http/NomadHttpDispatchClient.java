package com.pcrm.backend.nomad.http;

import com.pcrm.backend.exception.NomadDispatchException;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.nomad.NomadDispatchRequest;
import com.pcrm.backend.nomad.NomadDispatchResult;
import com.pcrm.backend.storage.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class NomadHttpDispatchClient implements NomadDispatchClient {

    private final RestClient restClient;
    private final String hclTemplate;
    private final String dockerComposeNetwork;
    private final StorageService storageService;

    public NomadHttpDispatchClient(
            String nomadBaseUrl,
            String jobTemplatePath,
            String dockerComposeNetwork,
            StorageService storageService
    ) throws IOException {
        this.restClient = RestClient.builder().baseUrl(nomadBaseUrl).build();
        this.hclTemplate = Files.readString(Path.of(jobTemplatePath));
        this.dockerComposeNetwork = dockerComposeNetwork;
        this.storageService = storageService;
    }

    @Override
    public NomadDispatchResult dispatchJob(NomadDispatchRequest request) {
        var existingJob = fetchExistingJob(request.nomadJobId());
        if (existingJob != null) {
            verifyExistingJobMatchesRun(existingJob, request);
            log.debug("Nomad job {} already exists for run {}", request.nomadJobId(), request.runId());
            return new NomadDispatchResult(request.nomadJobId(), null);
        }

        StringBuilder envVars = new StringBuilder();
        if (request.envVars() != null) {
            request.envVars().forEach((key, value) -> envVars.append(key)
                    .append(" = \"")
                    .append(escapeHcl(value))
                    .append("\"\n        "));
        }

        String artifactObjectKey = storageService.buildArtifactObjectKey(request.userId(), request.jobId());
        String artifactUploadUrl = storageService.generatePresignedUploadUrl(request.userId(), request.jobId());

        String renderedHcl = hclTemplate
                .replace("{{NOMAD_JOB_ID}}", escapeHcl(request.nomadJobId()))
                .replace("{{USER_ID}}", request.userId().toString())
                .replace("{{JOB_ID}}", request.jobId().toString())
                .replace("{{RUN_ID}}", request.runId().toString())
                .replace("{{TRACE_ID}}", valueOrEmpty(request.correlationId()))
                .replace("{{CORRELATION_ID}}", valueOrEmpty(request.correlationId()))
                .replace("{{QUOTA_RESERVATION_ID}}", valueOrEmpty(request.quotaReservationId()))
                .replace("{{RESOURCE_CLASS}}", escapeHcl(request.resourceClass()))
                .replace("{{DOCKER_IMAGE}}", escapeHcl(request.dockerImage()))
                .replace("{{EXECUTION_COMMAND}}", escapeHcl(request.executionCommand()))
                .replace("{{CORES}}", request.reqCpuCores().toString())
                .replace("{{MEMORY_MB}}", String.valueOf(request.reqRamGb() * 1024))
                .replace("{{ARTIFACT_OBJECT_KEY}}", escapeHcl(artifactObjectKey))
                .replace("{{ARTIFACT_UPLOAD_URL}}", escapeHcl(artifactUploadUrl))
                .replace("{{DOCKER_COMPOSE_NETWORK}}", escapeHcl(dockerComposeNetwork))
                .replace("{{ENV_VARS}}", envVars.toString());

        if (renderedHcl.contains("{{")) {
            throw new NomadDispatchException("Rendered Nomad job template contains unresolved placeholders");
        }

        try {
            var parsedResponse = restClient.post()
                    .uri("/v1/jobs/parse")
                    .body(Map.of("JobHCL", renderedHcl))
                    .retrieve()
                    .body(Map.class);

            if (parsedResponse == null) {
                throw new NomadDispatchException("Failed to parse HCL: Invalid response from Nomad");
            }

            var registerResponse = restClient.post()
                    .uri("/v1/jobs")
                    .body(Map.of("Job", parsedResponse))
                    .retrieve()
                    .body(Map.class);

            log.debug("Successfully registered Nomad job {} for run {}", request.nomadJobId(), request.runId());
            return new NomadDispatchResult(request.nomadJobId(), extractEvalId(registerResponse));
        } catch (RestClientResponseException ex) {
            log.error("Nomad API rejected the request! Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new NomadDispatchException("Nomad API error: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Unexpected error during Nomad dispatch", ex);
            throw new NomadDispatchException("Failed to register Nomad job " + request.nomadJobId() + ": " + ex.getMessage(), ex);
        }
    }

    private Map<?, ?> fetchExistingJob(String nomadJobId) {
        try {
            return restClient.get()
                    .uri("/v1/job/{jobId}", nomadJobId)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return null;
            }
            throw new NomadDispatchException("Failed to check existing Nomad job " + nomadJobId, ex);
        }
    }

    private void verifyExistingJobMatchesRun(Map<?, ?> existingJob, NomadDispatchRequest request) {
        if (!request.nomadJobId().equals(String.valueOf(existingJob.get("ID")))) {
            throw new NomadDispatchException("Existing Nomad job has unexpected ID for run " + request.runId());
        }

        Object rawMeta = existingJob.get("Meta");
        if (!(rawMeta instanceof Map<?, ?> meta)) {
            throw new NomadDispatchException("Existing Nomad job has no run metadata for " + request.nomadJobId());
        }

        var existingRunId = meta.get("run_id");
        if (!request.runId().toString().equals(existingRunId == null ? null : existingRunId.toString())) {
            throw new NomadDispatchException("Existing Nomad job metadata does not match run " + request.runId());
        }
    }

    private String escapeHcl(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String valueOrEmpty(Object input) {
        return input == null ? "" : escapeHcl(input.toString());
    }

    private String extractEvalId(Map<?, ?> registerResponse) {
        if (registerResponse == null) {
            return null;
        }
        var evalId = registerResponse.get("EvalID");
        return evalId == null ? null : evalId.toString();
    }
}
