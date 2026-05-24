package com.pcrm.backend.nomad;

public record NomadGpuDeviceSnapshot(
        String deviceId,
        String vendor,
        String type,
        String model,
        Integer memoryMiB,
        String health,
        String driverVersion
) {
}
