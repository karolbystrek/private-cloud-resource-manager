package com.pcrm.backend.nodes.dto;

import com.pcrm.backend.nodes.domain.Node;
import com.pcrm.backend.nodes.domain.NodeStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NodeDetailsResponse(
        String id,
        UUID nomadNodeId,
        String hostname,
        String ipAddress,
        NodeStatus status,
        String nomadStatus,
        String nomadStatusDescription,
        String schedulingEligibility,
        String datacenter,
        String nodePool,
        String nodeClass,
        boolean draining,
        String nomadVersion,
        String dockerVersion,
        Long nomadCreateIndex,
        Long nomadModifyIndex,
        int totalCpuCores,
        int totalRamMb,
        int totalGpuCount,
        String agentVersion,
        OffsetDateTime lastHeartbeat,
        OffsetDateTime createdAt
) {

    public static NodeDetailsResponse toNodeDetailsResponse(Node node) {
        return new NodeDetailsResponse(
                node.getId(),
                node.getNomadNodeId(),
                node.getHostname(),
                node.getIpAddress(),
                node.getStatus(),
                node.getNomadStatus(),
                node.getNomadStatusDescription(),
                node.getSchedulingEligibility(),
                node.getDatacenter(),
                node.getNodePool(),
                node.getNodeClass(),
                Boolean.TRUE.equals(node.getDraining()),
                node.getNomadVersion(),
                node.getDockerVersion(),
                node.getNomadCreateIndex(),
                node.getNomadModifyIndex(),
                node.getTotalCpuCores(),
                node.getTotalRamMb(),
                node.getTotalGpuCount(),
                node.getAgentVersion(),
                node.getLastHeartbeat(),
                node.getCreatedAt()
        );
    }
}
