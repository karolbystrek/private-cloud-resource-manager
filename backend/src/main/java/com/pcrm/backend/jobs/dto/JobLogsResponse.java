package com.pcrm.backend.jobs.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobLogsResponse(
        UUID jobId,
        String stream,
        String content,
        long capturedBytes,
        boolean truncated,
        boolean captureComplete,
        OffsetDateTime updatedAt
) {
}
