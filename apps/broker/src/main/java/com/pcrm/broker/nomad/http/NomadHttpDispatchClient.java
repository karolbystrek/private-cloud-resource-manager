package com.pcrm.broker.nomad.http;

import com.pcrm.broker.exception.NomadDispatchException;
import com.pcrm.broker.jobs.dto.JobSubmissionRequest;
import com.pcrm.broker.nomad.NomadDispatchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class NomadHttpDispatchClient implements NomadDispatchClient {

    private final RestClient restClient;
    private final String hclTemplate;

    public NomadHttpDispatchClient(
            @Value("${app.nomad.base-url}") String nomadBaseUrl,
            @Value("classpath:nomad/user-workload.hcl") Resource hclResource
    ) throws IOException {
        this.restClient = RestClient.builder().baseUrl(nomadBaseUrl).build();
        this.hclTemplate = Files.readString(hclResource.getFile().toPath());
    }

    @Override
    public void dispatchJob(UUID userId, UUID jobId, JobSubmissionRequest request) {

        // 1. Safely format user environment variables into HCL syntax
        StringBuilder envVars = new StringBuilder();
        if (request.envVars() != null) {
            request.envVars().forEach((key, value) -> envVars.append(key)
                    .append(" = \"")
                    .append(escapeHcl(value))
                    .append("\"\n        "));
        }

        // 2. Inject dynamic resources, config, and env vars
        String renderedHcl = hclTemplate
                .replace("{{USER_ID}}", userId.toString())
                .replace("{{JOB_ID}}", jobId.toString())
                .replace("{{DOCKER_IMAGE}}", escapeHcl(request.dockerImage()))
                .replace("{{EXECUTION_COMMAND}}", escapeHcl(request.executionCommand()))
                .replace("{{CORES}}", request.reqCpuCores().toString())
                .replace("{{MEMORY_MB}}", String.valueOf(request.reqRamGb() * 1024))
                .replace("{{ENV_VARS}}", envVars.toString());

        try {
            // 3. Parse the dynamically generated HCL into Nomad JSON
            var parsedResponse = restClient.post()
                    .uri("/v1/jobs/parse")
                    .body(Map.of("JobHCL", renderedHcl))
                    .retrieve()
                    .body(Map.class);

            if (parsedResponse == null) {
                throw new NomadDispatchException("Failed to parse HCL: Invalid response from Nomad");
            }

            // 4. Register the new job
            restClient.post()
                    .uri("/v1/jobs")
                    .body(Map.of("Job", parsedResponse))
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Successfully registered dynamic job#{} to Nomad", jobId);
        } catch (RestClientResponseException ex) {
            log.error("Nomad API rejected the request! Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new NomadDispatchException("Nomad API error: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Unexpected error during Nomad dispatch", ex);
            throw new NomadDispatchException("Failed to register job#" + jobId + ": " + ex.getMessage(), ex);
        }
    }

    // Prevents HCL injection by escaping backslashes and double quotes in user inputs.
    private String escapeHcl(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
