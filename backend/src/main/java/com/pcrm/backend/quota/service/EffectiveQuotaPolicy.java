package com.pcrm.backend.quota.service;

import com.pcrm.backend.user.UserRole;

public record EffectiveQuotaPolicy(
        UserRole role,
        long monthlyMinutes,
        int roleWeight,
        boolean unlimited,
        boolean overrideActive
) {
}
