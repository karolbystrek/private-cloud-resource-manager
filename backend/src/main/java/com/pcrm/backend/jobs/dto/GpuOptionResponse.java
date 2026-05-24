package com.pcrm.backend.jobs.dto;

public record GpuOptionResponse(
        String nodeId,
        String nodeHostname,
        String vendor,
        String model,
        Integer maxMemoryGb,
        Integer count
) {
}
