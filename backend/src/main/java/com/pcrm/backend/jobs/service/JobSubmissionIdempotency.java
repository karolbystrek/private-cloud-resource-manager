package com.pcrm.backend.jobs.service;

public record JobSubmissionIdempotency(String key, String fingerprint) {
}
