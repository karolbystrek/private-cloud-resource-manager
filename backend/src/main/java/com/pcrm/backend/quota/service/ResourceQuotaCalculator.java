package com.pcrm.backend.quota.service;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.quota.domain.ResourceWeightPolicy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ResourceQuotaCalculator {

    public QuotaUnits calculate(Job job, ResourceWeightPolicy policy) {
        long cpuUnits = Math.max(0, value(job.getReqCpuCores())) * (long) policy.getCpuCoreWeight();
        long ramUnits = ceilDiv(Math.max(0, value(job.getReqRamGb())), policy.getRamGbPerUnit())
                * (long) policy.getRamUnitWeight();
        long gpuUnits = calculateGpuUnits(job, policy.getGpuWeightTiers());
        return new QuotaUnits(cpuUnits, ramUnits, gpuUnits);
    }

    public long calculateComputeMinutes(long realMinutes, long totalUnits) {
        return Math.max(0L, realMinutes) * Math.max(1L, totalUnits);
    }

    private long calculateGpuUnits(Job job, List<ResourceWeightPolicy.GpuWeightTier> tiers) {
        if (!Boolean.TRUE.equals(job.getGpuEnabled()) || job.getGpuCount() == null || job.getGpuCount() <= 0) {
            return 0L;
        }

        int minMemoryGb = job.getGpuMinMemoryGb() == null ? 0 : Math.max(0, job.getGpuMinMemoryGb());
        int gpuWeight = tiers.stream()
                .filter(tier -> minMemoryGb >= tier.minMemoryGb())
                .max(Comparator.comparingInt(ResourceWeightPolicy.GpuWeightTier::minMemoryGb))
                .map(ResourceWeightPolicy.GpuWeightTier::weight)
                .orElse(16);
        return job.getGpuCount() * (long) gpuWeight;
    }

    private long ceilDiv(long value, long divisor) {
        if (value <= 0) {
            return 0L;
        }
        return (value + divisor - 1L) / divisor;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    public record QuotaUnits(
            long cpuUnits,
            long ramUnits,
            long gpuUnits
    ) {
        public long totalUnits() {
            return cpuUnits + ramUnits + gpuUnits;
        }
    }
}
