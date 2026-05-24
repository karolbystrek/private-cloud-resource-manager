package com.pcrm.backend.jobs.dto;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobHistoryItemResponse(
        UUID id,
        JobStatus status,
        String dockerImage,
        String executionCommand,
        int reqCpuCores,
        int reqRamGb,
        GpuRequirement gpuRequirement,
        QuotaBreakdown quotaBreakdown,
        long totalConsumedMinutes,
        OffsetDateTime createdAt
) {

    public static JobHistoryItemResponse from(Job job) {
        return new JobHistoryItemResponse(
                job.getId(),
                job.getStatus(),
                job.getDockerImage(),
                job.getExecutionCommand(),
                job.getReqCpuCores(),
                job.getReqRamGb(),
                GpuRequirement.fromJob(job),
                QuotaBreakdown.fromJob(job),
                job.getTotalConsumedMinutes(),
                job.getCreatedAt()
        );
    }
}
