package com.pcrm.backend.exception;

import org.springframework.security.core.AuthenticationException;

public class TokenRefreshException extends AuthenticationException {
    public TokenRefreshException(String message) {
        super(message);
    }

    public static TokenRefreshException tokenRefreshException() {
        return new TokenRefreshException("Token refresh failed");
    }
}
