package com.pcrm.backend.nomad;

import com.pcrm.backend.jobs.dto.GpuRequirement;

import java.util.Map;
import java.util.UUID;

public record NomadDispatchRequest(
        UUID userId,
        UUID jobId,
        String nomadJobId,
        String dockerImage,
        String executionCommand,
        Integer reqCpuCores,
        Integer reqRamGb,
        GpuRequirement gpuRequirement,
        Map<String, String> envVars,
        UUID correlationId
) {
}
