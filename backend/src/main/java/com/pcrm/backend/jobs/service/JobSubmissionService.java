package com.pcrm.backend.jobs.service;

import com.pcrm.backend.idempotency.service.IdempotencyService;
import com.pcrm.backend.idempotency.service.IdempotentWorkflow;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class JobSubmissionService {

    private static final String ACTOR_TYPE_USER = "USER";
    private static final String WORKFLOW_JOB_SUBMIT = "job.submit";
    private static final String RESOURCE_TYPE_JOB = "JOB";

    private final IdempotencyService idempotencyService;
    private final JobSubmissionPersistenceService persistenceService;
    private final TransactionTemplate submissionTransactionTemplate;

    public JobSubmissionService(
            IdempotencyService idempotencyService,
            JobSubmissionPersistenceService persistenceService,
            PlatformTransactionManager transactionManager
    ) {
        this.idempotencyService = idempotencyService;
        this.persistenceService = persistenceService;
        this.submissionTransactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PreparedJobSubmission submitJob(UUID userId, JobSubmissionRequest request, String idempotencyKey) {
        var preparedSubmission = submissionTransactionTemplate.execute(
                _ -> idempotencyService.execute(new IdempotentWorkflow<>(
                        ACTOR_TYPE_USER,
                        userId.toString(),
                        WORKFLOW_JOB_SUBMIT,
                        idempotencyKey,
                        request,
                        context -> persistenceService.prepareSubmission(
                                userId,
                                request,
                                context.key(),
                                context.requestFingerprint()
                        ),
                        responseBody -> PreparedJobSubmission.replayed(
                                UUID.fromString(responseBody.get("jobId").asText()),
                                UUID.fromString(responseBody.get("runId").asText()),
                                userId
                        ),
                        RESOURCE_TYPE_JOB,
                        PreparedJobSubmission::jobId
                )).response()
        );

        if (preparedSubmission == null) {
            throw new IllegalStateException("Failed to prepare job submission");
        }

        return preparedSubmission;
    }
}
