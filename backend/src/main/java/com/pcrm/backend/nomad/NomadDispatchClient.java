package com.pcrm.backend.nomad;

import com.pcrm.backend.jobs.dto.JobSubmissionRequest;

import java.util.UUID;

public interface NomadDispatchClient {

    void dispatchJob(UUID userId, UUID jobId, JobSubmissionRequest request);
}
