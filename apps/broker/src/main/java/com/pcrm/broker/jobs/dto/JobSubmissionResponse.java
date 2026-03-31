package com.pcrm.broker.jobs.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record JobSubmissionResponse(@JsonProperty("job_id") UUID jobId) {
}
