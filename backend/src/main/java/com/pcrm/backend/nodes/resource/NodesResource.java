package com.pcrm.backend.nodes.resource;

import com.pcrm.backend.nodes.dto.NodeResponse;
import com.pcrm.backend.nodes.service.NodeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/nodes")
public class NodesResource {

    private final NodeQueryService nodeQueryService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<NodeResponse> listNodes() {
        return nodeQueryService.listNodes();
    }
}
