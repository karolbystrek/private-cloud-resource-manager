package com.pcrm.backend.jobs.dto;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.RunStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobDetailsResponse(
        UUID id,
        UUID runId,
        RunStatus status,
        String dockerImage,
        String executionCommand,
        int reqCpuCores,
        int reqRamGb,
        long totalConsumedMinutes,
        String nodeId,
        OffsetDateTime createdAt,
        UUID userId,
        String userEmail
) {

    public static JobDetailsResponse from(Job job, String userEmail) {
        var run = job.getCurrentRun();
        return new JobDetailsResponse(
                job.getId(),
                run == null ? null : run.getId(),
                run == null ? job.getStatus() : run.getStatus(),
                job.getDockerImage(),
                job.getExecutionCommand(),
                job.getReqCpuCores(),
                job.getReqRamGb(),
                run == null ? job.getTotalConsumedMinutes() : run.getTotalConsumedMinutes(),
                job.getNodeId(),
                job.getCreatedAt(),
                job.getProfile().getId(),
                userEmail
        );
    }
}
