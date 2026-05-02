package com.pcrm.backend.events.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

public final class AggregateIds {

    public static final String JOB = "JOB";
    public static final String RUN = "RUN";
    public static final String SCHEDULE = "SCHEDULE";
    public static final String QUOTA_BALANCE = "QUOTA_BALANCE";

    private AggregateIds() {
    }

    public static String job(UUID jobId) {
        return uuidId(jobId, "jobId");
    }

    public static String run(UUID runId) {
        return uuidId(runId, "runId");
    }

    public static String schedule(UUID scheduleId) {
        return uuidId(scheduleId, "scheduleId");
    }

    public static String quotaBalance(UUID userId, OffsetDateTime intervalStartUtc, String resourceClass) {
        return String.join(":",
                uuidId(userId, "userId"),
                utcInstant(intervalStartUtc, "intervalStartUtc"),
                resourceClassCode(resourceClass));
    }

    private static String uuidId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return id.toString();
    }

    private static String utcInstant(OffsetDateTime value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString();
    }

    private static String resourceClassCode(String resourceClass) {
        if (resourceClass == null || resourceClass.isBlank()) {
            throw new IllegalArgumentException("resourceClass is required");
        }
        return resourceClass.trim().toLowerCase(Locale.ROOT);
    }
}
