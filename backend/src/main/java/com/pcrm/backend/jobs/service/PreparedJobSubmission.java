package com.pcrm.backend.jobs.service;

import java.util.UUID;

public record PreparedJobSubmission(UUID jobId, UUID userId, boolean replayed) {

    public static PreparedJobSubmission created(UUID jobId, UUID userId) {
        return new PreparedJobSubmission(jobId, userId, false);
    }

    public static PreparedJobSubmission replayed(UUID jobId, UUID userId) {
        return new PreparedJobSubmission(jobId, userId, true);
    }
}
