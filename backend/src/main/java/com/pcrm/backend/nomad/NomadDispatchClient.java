package com.pcrm.backend.nomad;

import com.pcrm.backend.jobs.dto.JobSubmissionRequest;

import java.util.UUID;

public interface NomadDispatchClient {

    NomadDispatchResult dispatchJob(UUID userId, UUID jobId, UUID runId, JobSubmissionRequest request);
}
