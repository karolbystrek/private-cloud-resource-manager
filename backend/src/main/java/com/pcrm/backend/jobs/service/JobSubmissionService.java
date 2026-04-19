package com.pcrm.backend.jobs.service;

import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class JobSubmissionService {

    private final JobSubmissionIdempotencyService idempotencyService;
    private final JobSubmissionPersistenceService persistenceService;
    private final TransactionTemplate submissionTransactionTemplate;

    public JobSubmissionService(
            JobSubmissionIdempotencyService idempotencyService,
            JobSubmissionPersistenceService persistenceService,
            PlatformTransactionManager transactionManager
    ) {
        this.idempotencyService = idempotencyService;
        this.persistenceService = persistenceService;
        this.submissionTransactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PreparedJobSubmission submitJob(UUID userId, JobSubmissionRequest request, String idempotencyKey) {
        var idempotency = idempotencyService.build(idempotencyKey, request);
        return executeSubmissionTransaction(userId, request, idempotency);
    }

    private PreparedJobSubmission executeSubmissionTransaction(
            UUID userId,
            JobSubmissionRequest request,
            JobSubmissionIdempotency idempotency
    ) {
        var preparedSubmission = submissionTransactionTemplate.execute(
                _ -> persistenceService.prepareSubmission(userId, request, idempotency)
        );

        if (preparedSubmission == null) {
            throw new IllegalStateException("Failed to prepare job submission");
        }

        return preparedSubmission;
    }
}
