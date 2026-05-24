package com.pcrm.backend.jobs.dto;

import com.pcrm.backend.jobs.domain.Job;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record GpuRequirement(
        Boolean enabled,

        @Min(0)
        Integer count,

        @Size(max = 40)
        String vendor,

        @Min(1)
        Integer minMemoryGb,

        @Size(max = 120)
        String model
) {

    public static final String NVIDIA_VENDOR = "nvidia";

    public static GpuRequirement disabled() {
        return new GpuRequirement(false, 0, null, null, null);
    }

    public static GpuRequirement fromJob(Job job) {
        if (!Boolean.TRUE.equals(job.getGpuEnabled())) {
            return disabled();
        }
        return new GpuRequirement(
                true,
                job.getGpuCount(),
                job.getGpuVendor(),
                job.getGpuMinMemoryGb(),
                job.getGpuModel()
        ).normalized();
    }

    public boolean requested() {
        return Boolean.TRUE.equals(enabled);
    }

    public GpuRequirement normalized() {
        if (!requested()) {
            return disabled();
        }

        var normalizedVendor = isBlank(vendor) ? NVIDIA_VENDOR : vendor.trim().toLowerCase();
        var normalizedModel = isBlank(model) ? null : model.trim();

        return new GpuRequirement(
                true,
                count,
                normalizedVendor,
                minMemoryGb,
                normalizedModel
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
