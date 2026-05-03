package com.pcrm.backend.events.service;

import java.util.List;
import java.util.Set;

public final class EventTopics {

    public static final String JOB_SUBMITTED = "job.submitted";
    public static final String RUN_CREATED = "run.created";
    public static final String RUN_LEASE_RESERVED = "run.lease.reserved";
    public static final String JOB_QUEUED = "job.queued";
    public static final String RUN_QUEUED = "run.queued";
    public static final String RUN_DISPATCH_REQUESTED = "run.dispatch.requested";
    public static final String RUN_DISPATCHED = "run.dispatched";
    public static final String RUN_STARTED = "run.started";
    public static final String RUN_PROCESS_SUCCEEDED = "run.process.succeeded";
    public static final String RUN_PROCESS_FAILED = "run.process.failed";
    public static final String RUN_FINALIZING = "run.finalizing";
    public static final String RUN_FAILED = "run.failed";
    public static final String RUN_CANCELED = "run.canceled";
    public static final String RUN_TIMED_OUT = "run.timed_out";
    public static final String RUN_INFRA_FAILED = "run.infra_failed";
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
            RUN_CREATED,
            RUN_LEASE_RESERVED,
            JOB_QUEUED,
            RUN_QUEUED,
            RUN_DISPATCH_REQUESTED,
            RUN_DISPATCHED,
            RUN_STARTED,
            RUN_PROCESS_SUCCEEDED,
            RUN_PROCESS_FAILED,
            RUN_FINALIZING,
            RUN_FAILED,
            RUN_CANCELED,
            RUN_TIMED_OUT,
            RUN_INFRA_FAILED,
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
