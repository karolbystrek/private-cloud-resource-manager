package com.pcrm.backend.jobs.dto;

import java.util.UUID;

public record JobSubmissionResponse(UUID jobId, UUID runId) {
}
