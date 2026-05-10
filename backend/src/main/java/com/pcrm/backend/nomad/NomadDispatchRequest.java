package com.pcrm.backend.nomad;

import java.util.Map;
import java.util.UUID;

public record NomadDispatchRequest(
        UUID userId,
        UUID jobId,
        UUID runId,
        UUID quotaReservationId,
        String resourceClass,
        String nomadJobId,
        String dockerImage,
        String executionCommand,
        Integer reqCpuCores,
        Integer reqRamGb,
        Map<String, String> envVars,
        UUID correlationId
) {
}
