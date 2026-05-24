package com.pcrm.backend.jobs.dto;

import com.pcrm.backend.jobs.validation.ValidEnvironmentVariables;
import com.pcrm.backend.jobs.validation.ValidGpuRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record JobSubmissionRequest(
        @NotBlank
        @Size(max = 255)
        String dockerImage,

        @NotBlank
        String executionCommand,

        @NotNull
        @Min(1)
        Integer reqCpuCores,

        @NotNull
        @Min(1)
        Integer reqRamGb,

        @Valid
        @ValidGpuRequirement
        GpuRequirement gpuRequirement,

        @NotNull
        @ValidEnvironmentVariables
        Map<String, String> envVars
) {
}
