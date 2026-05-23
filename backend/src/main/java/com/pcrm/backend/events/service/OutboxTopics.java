package com.pcrm.backend.events.service;

public final class OutboxTopics {

    public static final String JOB_SUBMITTED = "JobSubmitted";
    public static final String JOB_QUEUED = "JobQueued";

    private OutboxTopics() {
    }
}
