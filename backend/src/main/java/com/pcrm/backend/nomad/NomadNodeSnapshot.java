package com.pcrm.backend.nomad;

import java.util.List;
import java.util.UUID;

public record NomadNodeSnapshot(
        String id,
        UUID nomadNodeId,
        String hostname,
        String ipAddress,
        String status,
        String statusDescription,
        String schedulingEligibility,
        String datacenter,
        String nodePool,
        String nodeClass,
        boolean drain,
        String nomadVersion,
        String dockerVersion,
        Long nomadCreateIndex,
        Long nomadModifyIndex,
        int totalCpuCores,
        int totalRamMb,
        String agentVersion,
        List<NomadGpuDeviceSnapshot> gpuDevices
) {
}
