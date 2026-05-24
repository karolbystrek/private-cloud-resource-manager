package com.pcrm.backend.nodes.dto;

import com.pcrm.backend.nodes.domain.NodeGpuDevice;

public record NodeGpuDeviceResponse(
        String deviceId,
        String vendor,
        String type,
        String model,
        Integer memoryMiB,
        String health,
        String driverVersion
) {

    public static NodeGpuDeviceResponse from(NodeGpuDevice device) {
        return new NodeGpuDeviceResponse(
                device.getDeviceId(),
                device.getVendor(),
                device.getType(),
                device.getModel(),
                device.getMemoryMiB(),
                device.getHealth(),
                device.getDriverVersion()
        );
    }
}
