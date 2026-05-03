package com.pcrm.backend.jobs.service;

import java.util.UUID;

public record PreparedJobSubmission(UUID jobId, UUID runId, UUID userId, long initialReservedMinutes, boolean replayed) {

    public static PreparedJobSubmission created(UUID jobId, UUID runId, UUID userId, long initialReservedMinutes) {
        return new PreparedJobSubmission(jobId, runId, userId, initialReservedMinutes, false);
    }

    public static PreparedJobSubmission replayed(UUID jobId, UUID runId, UUID userId) {
        return new PreparedJobSubmission(jobId, runId, userId, 0, true);
    }
}
