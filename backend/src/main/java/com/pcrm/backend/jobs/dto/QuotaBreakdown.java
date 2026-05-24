package com.pcrm.backend.jobs.dto;

import com.pcrm.backend.jobs.domain.Job;

public record QuotaBreakdown(
        long cpuUnits,
        long ramUnits,
        long gpuUnits,
        long totalUnits
) {

    public static QuotaBreakdown fromJob(Job job) {
        return new QuotaBreakdown(
                value(job.getQuotaCpuUnits()),
                value(job.getQuotaRamUnits()),
                value(job.getQuotaGpuUnits()),
                value(job.getQuotaTotalUnits())
        );
    }

    private static long value(Long value) {
        return value == null ? 0L : value;
    }
}
