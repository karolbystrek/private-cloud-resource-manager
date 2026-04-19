package com.pcrm.backend.jobs.domain;

public enum JobStatus {
    QUEUED,
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    OOM_KILLED,
    LEASE_EXPIRED,
    STOPPED
}
