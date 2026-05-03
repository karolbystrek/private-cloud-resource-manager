package com.pcrm.backend.idempotency.service;

public record IdempotencyContext(String key, String requestFingerprint) {
}
