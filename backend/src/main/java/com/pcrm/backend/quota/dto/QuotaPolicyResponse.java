package com.pcrm.backend.quota.dto;

import com.pcrm.backend.user.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QuotaPolicyResponse(
        UUID id,
        UUID userId,
        UserRole role,
        long monthlyMinutes,
        int roleWeight,
        boolean unlimited,
        OffsetDateTime activeFrom,
        OffsetDateTime expiresAt
) {
}
