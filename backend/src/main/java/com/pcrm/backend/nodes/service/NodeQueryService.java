package com.pcrm.backend.nodes.service;

import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.nodes.dto.NodeDetailsResponse;
import com.pcrm.backend.nodes.dto.NodeGpuDeviceResponse;
import com.pcrm.backend.nodes.dto.NodeResponse;
import com.pcrm.backend.nodes.repository.NodeGpuDeviceRepository;
import com.pcrm.backend.nodes.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeQueryService {

    private final NodeRepository nodeRepository;
    private final NodeGpuDeviceRepository nodeGpuDeviceRepository;

    @Transactional(readOnly = true)
    public List<NodeResponse> listNodes() {
        return nodeRepository.findAllByOrderByHostnameAsc()
                .stream()
                .map(node -> NodeResponse.toNodeResponse(
                        node,
                        nodeGpuDeviceRepository.findByNodeIdOrderByModelAscDeviceIdAsc(node.getId()).size()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public NodeDetailsResponse getNodeDetails(String nodeId) {
        var node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node", "id", nodeId));
        var gpuDevices = nodeGpuDeviceRepository.findByNodeIdOrderByModelAscDeviceIdAsc(nodeId)
                .stream()
                .map(NodeGpuDeviceResponse::from)
                .toList();
        return NodeDetailsResponse.toNodeDetailsResponse(node, gpuDevices);
    }
}
