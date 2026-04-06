package com.pcrm.backend.jobs.service;

import com.pcrm.backend.exception.NomadDispatchException;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.nomad.NomadDispatchClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class JobSubmissionService {

    private final NomadDispatchClient nomadDispatchClient;
    private final JobSubmissionIdempotencyService idempotencyService;
    private final JobSubmissionPersistenceService persistenceService;
    private final TransactionTemplate submissionTransactionTemplate;
    private final TransactionTemplate compensationTransactionTemplate;

    public JobSubmissionService(
            NomadDispatchClient nomadDispatchClient,
            JobSubmissionIdempotencyService idempotencyService,
            JobSubmissionPersistenceService persistenceService,
            PlatformTransactionManager transactionManager
    ) {
        this.nomadDispatchClient = nomadDispatchClient;
        this.idempotencyService = idempotencyService;
        this.persistenceService = persistenceService;
        this.submissionTransactionTemplate = new TransactionTemplate(transactionManager);

        DefaultTransactionDefinition compensationDefinition = new DefaultTransactionDefinition();
        compensationDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.compensationTransactionTemplate = new TransactionTemplate(transactionManager, compensationDefinition);
    }

    public PreparedJobSubmission submitJob(UUID userId, JobSubmissionRequest request, String idempotencyKey) {
        var idempotency = idempotencyService.build(idempotencyKey, request);
        var preparedSubmission = executeSubmissionTransaction(userId, request, idempotency);

        if (preparedSubmission.replayed()) {
            return preparedSubmission;
        }

        dispatchJob(userId, request, preparedSubmission);
        return preparedSubmission;
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

    private void dispatchJob(
            UUID userId,
            JobSubmissionRequest request,
            PreparedJobSubmission preparedSubmission
    ) {
        try {
            nomadDispatchClient.dispatchJob(userId, preparedSubmission.jobId(), request);
        } catch (NomadDispatchException ex) {
            compensationTransactionTemplate.executeWithoutResult(
                    _ -> persistenceService.compensateFailedDispatch(preparedSubmission)
            );
            throw ex;
        }
    }
}
