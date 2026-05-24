package com.pcrm.backend.quota.service;

import com.pcrm.backend.quota.domain.ResourceWeightPolicy;
import com.pcrm.backend.quota.dto.ResourceWeightPolicyResponse;
import com.pcrm.backend.quota.dto.admin.UpsertResourceWeightPolicyRequest;
import com.pcrm.backend.quota.repository.ResourceWeightPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceWeightPolicyService {

    private final ResourceWeightPolicyRepository repository;

    @Transactional(readOnly = true)
    public ResourceWeightPolicy getPolicy() {
        return repository.findById(ResourceWeightPolicy.SINGLETON_ID)
                .orElseGet(this::defaultPolicy);
    }

    @Transactional(readOnly = true)
    public ResourceWeightPolicyResponse getPolicyResponse() {
        return ResourceWeightPolicyResponse.from(getPolicy());
    }

    @Transactional
    public ResourceWeightPolicyResponse updatePolicy(UpsertResourceWeightPolicyRequest request) {
        var tiers = normalizeTiers(request.gpuWeightTiers());
        var policy = repository.findById(ResourceWeightPolicy.SINGLETON_ID)
                .orElseGet(this::defaultPolicy);

        policy.setCpuCoreWeight(request.cpuCoreWeight());
        policy.setRamGbPerUnit(request.ramGbPerUnit());
        policy.setRamUnitWeight(request.ramUnitWeight());
        policy.setGpuWeightTiers(tiers);
        policy.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return ResourceWeightPolicyResponse.from(repository.save(policy));
    }

    private List<ResourceWeightPolicy.GpuWeightTier> normalizeTiers(
            List<UpsertResourceWeightPolicyRequest.GpuWeightTierRequest> requestedTiers
    ) {
        Set<Integer> thresholds = requestedTiers.stream()
                .map(UpsertResourceWeightPolicyRequest.GpuWeightTierRequest::minMemoryGb)
                .collect(Collectors.toSet());
        if (thresholds.size() != requestedTiers.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GPU memory thresholds must be unique");
        }
        if (!thresholds.contains(0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GPU weight tiers must include a 0 GB default");
        }

        return requestedTiers.stream()
                .map(tier -> new ResourceWeightPolicy.GpuWeightTier(tier.minMemoryGb(), tier.weight()))
                .sorted(Comparator.comparingInt(ResourceWeightPolicy.GpuWeightTier::minMemoryGb))
                .toList();
    }

    private ResourceWeightPolicy defaultPolicy() {
        return ResourceWeightPolicy.builder()
                .id(ResourceWeightPolicy.SINGLETON_ID)
                .cpuCoreWeight(1)
                .ramGbPerUnit(4)
                .ramUnitWeight(1)
                .gpuWeightTiers(List.of(
                        new ResourceWeightPolicy.GpuWeightTier(0, 16),
                        new ResourceWeightPolicy.GpuWeightTier(16, 24),
                        new ResourceWeightPolicy.GpuWeightTier(24, 32),
                        new ResourceWeightPolicy.GpuWeightTier(40, 48)
                ))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }
}
