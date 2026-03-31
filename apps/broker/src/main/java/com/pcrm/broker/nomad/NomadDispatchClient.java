package com.pcrm.broker.nomad;

import com.pcrm.broker.jobs.dto.JobSubmissionRequest;

import java.util.UUID;

public interface NomadDispatchClient {

    void dispatchJob(UUID jobId, JobSubmissionRequest request);
}
