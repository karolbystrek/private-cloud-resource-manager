package com.pcrm.backend.quota.dto.admin;

import com.pcrm.backend.user.UserRole;

import java.util.UUID;

public record AdminQuotaUserOptionResponse(
        UUID userId,
        String email,
        UserRole role
) {
}
