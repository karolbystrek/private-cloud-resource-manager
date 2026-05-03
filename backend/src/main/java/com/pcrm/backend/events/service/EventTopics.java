package com.pcrm.backend.events.service;

import java.util.List;
import java.util.Set;

public final class EventTopics {

    public static final String JOB_SUBMITTED = "JobSubmitted";
    public static final String RUN_SUBMITTED = "RunSubmitted";
    public static final String RUN_CREATED = "RunCreated";
    public static final String RUN_LEASE_RESERVED = "RunLeaseReserved";
    public static final String JOB_QUEUED = "JobQueued";
    public static final String RUN_QUEUED = "RunQueued";
    public static final String RUN_DISPATCH_REQUESTED = "RunDispatchRequested";
    public static final String RUN_DISPATCHED = "RunDispatched";
    public static final String RUN_STARTED = "RunStarted";
    public static final String RUN_PROCESS_SUCCEEDED = "RunProcessSucceeded";
    public static final String RUN_PROCESS_FAILED = "RunProcessFailed";
    public static final String RUN_FINALIZING = "RunFinalizing";
    public static final String RUN_FAILED = "RunFailed";
    public static final String RUN_CANCELED = "RunCanceled";
    public static final String RUN_TIMED_OUT = "RunTimedOut";
    public static final String RUN_INFRA_FAILED = "RunInfraFailed";
    public static final String JOB_DISPATCH_REQUESTED = "JobDispatchRequested";
    public static final String JOB_DISPATCHED = "JobDispatched";
    public static final String JOB_STARTED = "JobStarted";
    public static final String JOB_FINISHED = "JobFinished";
    public static final String QUOTA_RESERVED = "QuotaReserved";
    public static final String QUOTA_REJECTED = "QuotaRejected";
    public static final String QUOTA_CONSUMED = "QuotaConsumed";
    public static final String QUOTA_RELEASED = "QuotaReleased";
    public static final String NOMAD_SIGNAL_RECEIVED = "NomadSignalReceived";

    public static final Set<String> CURRENT_SERVICE_TOPICS = Set.of(
            JOB_SUBMITTED,
            RUN_SUBMITTED,
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
            QUOTA_REJECTED,
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
