package com.pcrm.backend.quota.dto;

import com.pcrm.backend.quota.domain.ResourceWeightPolicy;

import java.time.OffsetDateTime;
import java.util.List;

public record ResourceWeightPolicyResponse(
        int cpuCoreWeight,
        int ramGbPerUnit,
        int ramUnitWeight,
        List<GpuWeightTierResponse> gpuWeightTiers,
        OffsetDateTime updatedAt
) {

    public static ResourceWeightPolicyResponse from(ResourceWeightPolicy policy) {
        return new ResourceWeightPolicyResponse(
                policy.getCpuCoreWeight(),
                policy.getRamGbPerUnit(),
                policy.getRamUnitWeight(),
                policy.getGpuWeightTiers().stream()
                        .map(tier -> new GpuWeightTierResponse(tier.minMemoryGb(), tier.weight()))
                        .toList(),
                policy.getUpdatedAt()
        );
    }

    public record GpuWeightTierResponse(
            int minMemoryGb,
            int weight
    ) {
    }
}
