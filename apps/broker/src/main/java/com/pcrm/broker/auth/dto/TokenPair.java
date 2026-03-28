package com.pcrm.broker.auth.dto;

public record TokenPair(
        String accessToken,
        String refreshToken
) {
}
