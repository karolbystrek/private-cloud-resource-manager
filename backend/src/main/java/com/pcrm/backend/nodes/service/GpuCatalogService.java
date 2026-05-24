package com.pcrm.backend.nodes.service;

import com.pcrm.backend.exception.JobSubmissionValidationException;
import com.pcrm.backend.jobs.dto.GpuOptionResponse;
import com.pcrm.backend.jobs.dto.GpuRequirement;
import com.pcrm.backend.nodes.repository.NodeGpuDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GpuCatalogService {

    private final NodeGpuDeviceRepository nodeGpuDeviceRepository;

    @Transactional(readOnly = true)
    public List<GpuOptionResponse> listAvailableGpuOptions() {
        return nodeGpuDeviceRepository.findAvailableGpuOptions()
                .stream()
                .map(row -> new GpuOptionResponse(
                        row.getNodeId(),
                        row.getNodeHostname(),
                        row.getVendor(),
                        row.getModel(),
                        toGb(row.getMaxMemoryMiB()),
                        Math.toIntExact(row.getDeviceCount())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public void validateRequirement(GpuRequirement rawRequirement) {
        var requirement = rawRequirement == null ? GpuRequirement.disabled() : rawRequirement.normalized();
        if (!requirement.requested()) {
            return;
        }

        Integer minMemoryMiB = requirement.minMemoryGb() == null ? null : requirement.minMemoryGb() * 1024;
        boolean available = nodeGpuDeviceRepository.hasAvailableGpuCapacity(
                requirement.vendor(),
                requirement.model(),
                minMemoryMiB,
                requirement.count()
        );

        if (!available) {
            throw new JobSubmissionValidationException(
                    "Requested GPU is not available on any eligible node.",
                    List.of(new JobSubmissionValidationException.FieldError(
                            "gpuRequirement.model",
                            "Select an available GPU model from the cluster inventory."
                    ))
            );
        }
    }

    private Integer toGb(Integer memoryMiB) {
        if (memoryMiB == null) {
            return null;
        }
        return Math.max(1, memoryMiB / 1024);
    }
}
