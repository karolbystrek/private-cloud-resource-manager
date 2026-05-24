package com.pcrm.backend.quota.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpsertResourceWeightPolicyRequest(
        @NotNull
        @Min(1)
        Integer cpuCoreWeight,

        @NotNull
        @Min(1)
        Integer ramGbPerUnit,

        @NotNull
        @Min(1)
        Integer ramUnitWeight,

        @NotEmpty
        List<@Valid GpuWeightTierRequest> gpuWeightTiers
) {

    public record GpuWeightTierRequest(
            @NotNull
            @Min(0)
            Integer minMemoryGb,

            @NotNull
            @Min(1)
            Integer weight
    ) {
    }
}
