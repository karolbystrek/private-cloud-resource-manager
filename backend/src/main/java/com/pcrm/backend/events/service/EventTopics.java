package com.pcrm.backend.events.service;

import java.util.List;
import java.util.Set;

public final class EventTopics {

    public static final String JOB_SUBMITTED = "JobSubmitted";
    public static final String JOB_QUEUED = "JobQueued";
    public static final String JOB_DISPATCH_REQUESTED = "JobDispatchRequested";
    public static final String JOB_DISPATCHED = "JobDispatched";
    public static final String JOB_SCHEDULED = "JobScheduled";
    public static final String JOB_STARTED = "JobStarted";
    public static final String JOB_FINALIZING = "JobFinalizing";
    public static final String JOB_SUCCEEDED = "JobSucceeded";
    public static final String JOB_FAILED = "JobFailed";
    public static final String JOB_CANCELED = "JobCanceled";
    public static final String JOB_TIMED_OUT = "JobTimedOut";
    public static final String JOB_INFRA_FAILED = "JobInfraFailed";
    public static final String JOB_LEASE_RENEWED = "JobLeaseRenewed";
    public static final String JOB_LEASE_STOP_REQUESTED = "JobLeaseStopRequested";
    public static final String JOB_LEASE_STOP_FAILED = "JobLeaseStopFailed";
    public static final String QUOTA_RESERVED = "QuotaReserved";
    public static final String QUOTA_REJECTED = "QuotaRejected";
    public static final String QUOTA_CONSUMED = "QuotaConsumed";
    public static final String QUOTA_RELEASED = "QuotaReleased";
    public static final String NOMAD_SIGNAL_RECEIVED = "NomadSignalReceived";

    public static final Set<String> CURRENT_SERVICE_TOPICS = Set.of(
            JOB_SUBMITTED,
            JOB_QUEUED,
            JOB_DISPATCH_REQUESTED,
            JOB_DISPATCHED,
            JOB_SCHEDULED,
            JOB_STARTED,
            JOB_FINALIZING,
            JOB_SUCCEEDED,
            JOB_FAILED,
            JOB_CANCELED,
            JOB_TIMED_OUT,
            JOB_INFRA_FAILED,
            JOB_LEASE_RENEWED,
            JOB_LEASE_STOP_REQUESTED,
            JOB_LEASE_STOP_FAILED,
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
