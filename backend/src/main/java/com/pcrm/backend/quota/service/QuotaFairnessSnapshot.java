package com.pcrm.backend.quota.service;

public record QuotaFairnessSnapshot(
        long allocatedMinutes,
        long consumedMinutes,
        int roleWeight,
        boolean unlimited
) {

    public double usageRatio() {
        if (unlimited || allocatedMinutes <= 0) {
            return 1.0d;
        }
        double ratio = consumedMinutes / (double) allocatedMinutes;
        return Math.max(0.0d, Math.min(1.0d, ratio));
    }
}
