package com.pcrm.backend.quota.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminQuotaGrantRequest(
        @NotNull
        UUID userId,

        @NotNull
        @Min(1)
        Long minutes,

        OffsetDateTime intervalStart,

        OffsetDateTime intervalEnd,

        @NotBlank
        @Size(max = 1000)
        String reason
) {
}
