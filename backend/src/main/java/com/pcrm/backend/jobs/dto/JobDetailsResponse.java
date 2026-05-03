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
        String username
) {

    public static JobDetailsResponse from(Job job) {
        return new JobDetailsResponse(
                job.getId(),
                job.getCurrentRun() == null ? null : job.getCurrentRun().getId(),
                job.getStatus(),
                job.getDockerImage(),
                job.getExecutionCommand(),
                job.getReqCpuCores(),
                job.getReqRamGb(),
                job.getTotalConsumedMinutes(),
                job.getNodeId(),
                job.getCreatedAt(),
                job.getUser().getId(),
                job.getUser().getUsername()
        );
    }
}
