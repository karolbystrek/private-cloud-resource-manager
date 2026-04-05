package com.pcrm.broker.nomad.http;

import com.pcrm.broker.exception.NomadDispatchException;
import com.pcrm.broker.jobs.dto.JobSubmissionRequest;
import com.pcrm.broker.nomad.NomadDispatchClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Slf4j
@Service
public class NomadHttpDispatchClient implements NomadDispatchClient {

    private static final String TEMPLATE_JOB_ID = "user-workload-template";

    private final RestClient restClient;
    private final String hclTemplate;

    public NomadHttpDispatchClient(
            @Value("${app.nomad.base-url}") String nomadBaseUrl,
            @Value("classpath:nomad/user-workload.hcl") Resource hclResource
    ) throws IOException {
        this.restClient = RestClient.builder().baseUrl(nomadBaseUrl).build();
        this.hclTemplate = Files.readString(hclResource.getFile().toPath());
    }

    @PostConstruct
    public void init() {
        try {
            // 1. Convert HCL to JSON Specification
            var parseResponse = restClient.post()
                    .uri("/v1/jobs/parse")
                    .body(Map.of("JobHCL", hclTemplate))
                    .retrieve()
                    .body(Map.class);

            // 2. Register the parsed job
            restClient.post()
                    .uri("/v1/jobs")
                    .body(Map.of("Job", parseResponse))
                    .retrieve()
                    .toBodilessEntity();

            System.out.println("Nomad job template registered successfully.");
        } catch (Exception e) {
            System.err.println("Failed to register Nomad template: " + e.getMessage());
        }
    }

    @Override
    public void dispatchJob(UUID jobId, JobSubmissionRequest request) {
        var payload = buildPayload(jobId, request);

        try {
            restClient.post()
                    .uri("/v1/job/{id}/dispatch", TEMPLATE_JOB_ID)
                    .contentType(APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (_, res) -> {
                        throw new NomadDispatchException("Nomad dispatch failed with status code: " + res.getStatusCode());
                    })
                    .toBodilessEntity();
            log.debug("Successfully dispatched job {} to Nomad with payload: {}", jobId, payload);
        } catch (Exception ex) {
            throw new NomadDispatchException("Failed to dispatch job " + jobId, ex);
        }
    }

    private Map<String, Object> buildPayload(UUID jobId, JobSubmissionRequest request) {
        return Map.of(
                "Meta", Map.of(
                        "DOCKER_IMAGE", request.dockerImage(),
                        "EXECUTION_COMMAND", request.executionCommand(),
                        "JOB_ID", jobId.toString()
                ),
                "Payload", "",
                "Resources", Map.of(
                        "CPU", 0, // Must be 0 if using cores
                        "Cores", request.reqCpuCores(),
                        "MemoryMB", request.reqRamGb() * 1024
                )
        );
    }
}
