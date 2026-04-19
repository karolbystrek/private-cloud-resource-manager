package com.pcrm.backend.quota.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record UpsertQuotaOverrideRequest(
        @NotNull
        @Min(0)
        Long monthlyMinutes,

        @NotNull
        @Min(1)
        Integer roleWeight,

        @NotNull
        Boolean unlimited,

        OffsetDateTime activeFrom,
        OffsetDateTime expiresAt
) {
}
