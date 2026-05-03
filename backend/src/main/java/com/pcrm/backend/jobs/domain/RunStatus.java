package com.pcrm.backend.jobs.domain;

public enum RunStatus {
    QUEUED,
    DISPATCHING,
    SCHEDULING,
    RUNNING,
    FINALIZING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    TIMED_OUT,
    INFRA_FAILED
}
