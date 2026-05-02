package com.pcrm.backend.events.service;

import java.util.List;
import java.util.Set;

public final class EventTopics {

    public static final String JOB_SUBMITTED = "job.submitted";
    public static final String JOB_QUEUED = "job.queued";
    public static final String JOB_DISPATCH_REQUESTED = "job.dispatch.requested";
    public static final String JOB_DISPATCHED = "job.dispatched";
    public static final String JOB_STARTED = "job.started";
    public static final String JOB_FINISHED = "job.finished";
    public static final String QUOTA_RESERVED = "quota.reserved";
    public static final String QUOTA_CONSUMED = "quota.consumed";
    public static final String QUOTA_RELEASED = "quota.released";
    public static final String NOMAD_SIGNAL_RECEIVED = "nomad.signal.received";

    public static final Set<String> CURRENT_SERVICE_TOPICS = Set.of(
            JOB_SUBMITTED,
            JOB_QUEUED,
            JOB_DISPATCH_REQUESTED,
            JOB_DISPATCHED,
            JOB_STARTED,
            JOB_FINISHED,
            QUOTA_RESERVED,
            QUOTA_CONSUMED,
            QUOTA_RELEASED,
            NOMAD_SIGNAL_RECEIVED
    );

    private EventTopics() {
    }

    public static List<String> topicsForEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return List.of();
        }
        return List.of(eventType);
    }
}
