package com.pcrm.broker.nomad.http;

import com.pcrm.broker.exception.NomadDispatchException;
import com.pcrm.broker.jobs.dto.JobSubmissionRequest;
import com.pcrm.broker.nomad.NomadDispatchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NomadHttpDispatchClient implements NomadDispatchClient {

    private final RestClient restClient;

    public NomadHttpDispatchClient(
            @Value("${app.nomad.base-url:http://localhost:4646}") String nomadBaseUrl
    ) {
        this.restClient = RestClient.builder().baseUrl(nomadBaseUrl).build();
    }

    @Override
    public void dispatchJob(UUID jobId, JobSubmissionRequest request) {
        Map<String, Object> payload = buildPayload(jobId, request);

        try {
            restClient.post()
                    .uri("/v1/jobs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (ResourceAccessException ex) {
            throw new NomadDispatchException("Nomad dispatch timed out", ex);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                throw new NomadDispatchException("Nomad dispatch failed with status " + ex.getStatusCode(), ex);
            }
            throw new NomadDispatchException("Nomad dispatch request was rejected", ex);
        } catch (RuntimeException ex) {
            throw new NomadDispatchException("Nomad dispatch failed", ex);
        }
    }

    private Map<String, Object> buildPayload(UUID jobId, JobSubmissionRequest request) {
        Map<String, Object> leaseTask = new HashMap<>();
        leaseTask.put("Name", "lease-enforcer");
        leaseTask.put("Driver", "docker");
        leaseTask.put("Config", Map.of("image", "pcrm/lease-enforcer:latest"));
        leaseTask.put("Lifecycle", Map.of("Hook", "prestart", "Sidecar", true));
        leaseTask.put("RestartPolicy", Map.of("Attempts", 0, "Mode", "fail"));
        leaseTask.put("Env", Map.of("JOB_ID", jobId.toString()));

        Map<String, Object> workloadResources = new HashMap<>();
        workloadResources.put("CPU", request.reqCpuCores() * 1000);
        workloadResources.put("MemoryMB", request.reqRamGb() * 1024);
        if (request.reqGpuCount() > 0) {
            workloadResources.put("Devices", List.of(Map.of("Name", "nvidia/gpu", "Count", request.reqGpuCount())));
        }

        Map<String, Object> userTask = new HashMap<>();
        userTask.put("Name", "user-workload");
        userTask.put("Driver", "docker");
        userTask.put("Config", Map.of(
                "image", request.dockerImage(),
                "command", "/bin/sh",
                "args", List.of("-c", request.executionCommand())
        ));
        userTask.put("Resources", workloadResources);

        Map<String, Object> uploaderTask = new HashMap<>();
        uploaderTask.put("Name", "artifact-uploader");
        uploaderTask.put("Driver", "docker");
        uploaderTask.put("Config", Map.of("image", "pcrm/artifact-uploader:latest"));
        uploaderTask.put("Lifecycle", Map.of("Hook", "poststop", "Sidecar", false));

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(leaseTask);
        tasks.add(userTask);
        tasks.add(uploaderTask);

        Map<String, Object> taskGroup = new HashMap<>();
        taskGroup.put("Name", "job-group");
        taskGroup.put("Tasks", tasks);

        Map<String, Object> job = new HashMap<>();
        job.put("ID", jobId.toString());
        job.put("Name", "job-" + jobId);
        job.put("Type", "batch");
        job.put("TaskGroups", List.of(taskGroup));

        return Map.of("Job", job);
    }
}
