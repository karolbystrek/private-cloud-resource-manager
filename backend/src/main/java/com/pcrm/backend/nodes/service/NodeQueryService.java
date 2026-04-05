package com.pcrm.backend.nodes.service;

import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.nodes.dto.NodeDetailsResponse;
import com.pcrm.backend.nodes.dto.NodeResponse;
import com.pcrm.backend.nodes.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeQueryService {

    private final NodeRepository nodeRepository;

    @Transactional(readOnly = true)
    public List<NodeResponse> listNodes() {
        return nodeRepository.findAllByOrderByHostnameAsc()
                .stream()
                .map(NodeResponse::toNodeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NodeDetailsResponse getNodeDetails(String nodeId) {
        return nodeRepository.findById(nodeId)
                .map(NodeDetailsResponse::toNodeDetailsResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Node", "id", nodeId));
    }
}
