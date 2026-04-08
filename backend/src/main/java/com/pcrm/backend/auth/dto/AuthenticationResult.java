package com.pcrm.backend.auth.dto;

import com.pcrm.backend.user.UserRole;

public record AuthenticationResult(
        TokenPair tokens,
        UserRole role
) {
}
