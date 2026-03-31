package com.pcrm.broker.jobs.service;

import java.util.UUID;

public record PreparedJobSubmission(UUID jobId, UUID userId, long initialLeaseCost) {
}
