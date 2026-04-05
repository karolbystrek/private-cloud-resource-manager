package com.pcrm.backend.auth.dto;

public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
