package com.pcrm.backend.quota.dto;

import com.pcrm.backend.user.UserRole;

import java.time.OffsetDateTime;

public record QuotaSummaryResponse(
        UserRole role,
        long allocatedMinutes,
        long reservedMinutes,
        long consumedMinutes,
        long remainingMinutes,
        boolean unlimited,
        int roleWeight,
        OffsetDateTime windowStart,
        OffsetDateTime windowEnd,
        OffsetDateTime resetAt
) {
}
