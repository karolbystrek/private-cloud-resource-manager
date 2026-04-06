package com.pcrm.backend.jobs.service;

import java.util.UUID;

public record PreparedJobSubmission(UUID jobId, UUID userId, long initialLeaseCost, boolean replayed) {

    public static PreparedJobSubmission created(UUID jobId, UUID userId, long initialLeaseCost) {
        return new PreparedJobSubmission(jobId, userId, initialLeaseCost, false);
    }

    public static PreparedJobSubmission replayed(UUID jobId, UUID userId) {
        return new PreparedJobSubmission(jobId, userId, 0, true);
    }
}
