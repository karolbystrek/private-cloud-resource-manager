package com.pcrm.backend.jobs.service;

import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.service.OutboxConsumerDedupeService;
import com.pcrm.backend.events.service.OutboxTopics;
import com.pcrm.backend.events.service.OutboxMessageHandler;
import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobAdmissionWorker implements OutboxMessageHandler {

    private static final String CONSUMER_NAME = "job-admission-worker";
    private static final String INSUFFICIENT_QUOTA = "INSUFFICIENT_QUOTA";

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final QuotaAccountingService quotaAccountingService;
    private final OutboxConsumerDedupeService dedupeService;
    private final JobOutboxPublisher outboxPublisher;

    @Override
    public String topic() {
        return OutboxTopics.JOB_SUBMITTED;
    }

    @Override
    public void handle(OutboxMessage message) {
        dedupeService.runOnce(CONSUMER_NAME, message.getId(), () -> admitJob(message));
    }

    private void admitJob(OutboxMessage message) {
        var jobId = extractJobId(message);
        var job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", jobId));

        if (job.getStatus() != JobStatus.SUBMITTED) {
            log.debug("Skipping admission for job {} with status {}", job.getId(), job.getStatus());
            return;
        }

        var correlationId = extractCorrelationId(message);
        try {
            admitWithQuotaReservation(job, correlationId);
        } catch (InsufficientQuotaException ex) {
            rejectForInsufficientQuota(job, correlationId, ex);
        }
    }

    private void admitWithQuotaReservation(Job job, UUID correlationId) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var reservedMinutes = quotaAccountingService.reserveInitialLease(
                job.getProfile().getId(),
                job,
                "Initial lease reserved during admission"
        );

        jobStateMachine.markQueued(job, now, reservedMinutes, quotaAccountingService.getLeaseMinutes());

        outboxPublisher.jobQueued(job, correlationId);
        log.debug("Admitted job {} with {} reserved minutes", job.getId(), reservedMinutes);
    }

    private void rejectForInsufficientQuota(Job job, UUID correlationId, InsufficientQuotaException ex) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jobStateMachine.markRejectedForInsufficientQuota(job, now, INSUFFICIENT_QUOTA);

        log.debug("Rejected job {} for insufficient quota", job.getId());
    }

    private UUID extractJobId(OutboxMessage message) {
        var rawJobId = message.getPayload().path("jobId").asText(null);
        if (rawJobId == null || rawJobId.isBlank()) {
            throw new IllegalArgumentException(OutboxTopics.JOB_SUBMITTED + " payload is missing jobId");
        }
        return UUID.fromString(rawJobId);
    }

    private UUID extractCorrelationId(OutboxMessage message) {
        var rawCorrelationId = message.getHeaders().path("correlation_id").asText(null);
        if (rawCorrelationId == null || rawCorrelationId.isBlank()) {
            return UUID.randomUUID();
        }
        return UUID.fromString(rawCorrelationId);
    }
}
