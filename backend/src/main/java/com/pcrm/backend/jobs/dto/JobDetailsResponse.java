package com.pcrm.backend.jobs.dto;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobDetailsResponse(
        UUID id,
        JobStatus status,
        String dockerImage,
        String executionCommand,
        int reqCpuCores,
        int reqRamGb,
        long totalConsumedMinutes,
        OffsetDateTime createdAt,
        UUID userId,
        String userEmail
) {

    public static JobDetailsResponse from(Job job, String userEmail) {
        return new JobDetailsResponse(
                job.getId(),
                job.getStatus(),
                job.getDockerImage(),
                job.getExecutionCommand(),
                job.getReqCpuCores(),
                job.getReqRamGb(),
                job.getTotalConsumedMinutes(),
                job.getCreatedAt(),
                job.getProfile().getId(),
                userEmail
        );
    }
}
