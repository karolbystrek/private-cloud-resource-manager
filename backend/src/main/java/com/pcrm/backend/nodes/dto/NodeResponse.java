package com.pcrm.backend.nodes.dto;

import com.pcrm.backend.nodes.domain.Node;
import com.pcrm.backend.nodes.domain.NodeStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NodeResponse(
        String id,
        UUID nomadNodeId,
        String hostname,
        String ipAddress,
        NodeStatus status,
        int totalCpuCores,
        int totalRamMb,
        OffsetDateTime lastHeartbeat
) {

    public static NodeResponse toNodeResponse(Node node) {
        return new NodeResponse(
                node.getId(),
                node.getNomadNodeId(),
                node.getHostname(),
                node.getIpAddress(),
                node.getStatus(),
                node.getTotalCpuCores(),
                node.getTotalRamMb(),
                node.getLastHeartbeat()
        );
    }
}
