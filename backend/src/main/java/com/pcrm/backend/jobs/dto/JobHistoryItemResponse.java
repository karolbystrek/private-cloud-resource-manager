package com.pcrm.backend.jobs.dto;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.RunStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobHistoryItemResponse(
        UUID id,
        UUID runId,
        String nodeId,
        RunStatus status,
        String dockerImage,
        String executionCommand,
        int reqCpuCores,
        int reqRamGb,
        long totalConsumedMinutes,
        OffsetDateTime createdAt
) {

    public static JobHistoryItemResponse from(Job job) {
        var run = job.getCurrentRun();
        return new JobHistoryItemResponse(
                job.getId(),
                run == null ? null : run.getId(),
                job.getNodeId(),
                run == null ? job.getStatus() : run.getStatus(),
                job.getDockerImage(),
                job.getExecutionCommand(),
                job.getReqCpuCores(),
                job.getReqRamGb(),
                run == null ? job.getTotalConsumedMinutes() : run.getTotalConsumedMinutes(),
                job.getCreatedAt()
        );
    }
}
