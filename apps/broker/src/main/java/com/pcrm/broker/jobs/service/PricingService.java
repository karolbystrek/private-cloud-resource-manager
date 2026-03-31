package com.pcrm.broker.jobs.service;

import com.pcrm.broker.jobs.dto.JobSubmissionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PricingService {

    @Value("${app.pricing.base-cpu-rate-per-hour:40}")
    private long baseCpuRatePerHour;
    @Value("${app.pricing.base-ram-rate-per-hour:8}")
    private long baseRamRatePerHour;
    @Value("${app.pricing.base-gpu-rate-per-hour:120}")
    private long baseGpuRatePerHour;

    public long calculateInitialLeaseCost(JobSubmissionRequest request) {
        long hourlyCost = (long) request.reqCpuCores() * baseCpuRatePerHour
                + (long) request.reqRamGb() * baseRamRatePerHour
                + (long) request.reqGpuCount() * baseGpuRatePerHour;
        return Math.max(1L, (long) Math.ceil(hourlyCost / 4.0d));
    }
}
