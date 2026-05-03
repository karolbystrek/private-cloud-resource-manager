package com.pcrm.backend.jobs.service;

import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.service.AggregateIds;
import com.pcrm.backend.events.service.DomainEventAppendRequest;
import com.pcrm.backend.events.service.DomainEventAppender;
import com.pcrm.backend.events.service.EventConsumerDedupeService;
import com.pcrm.backend.events.service.EventTopics;
import com.pcrm.backend.events.service.OutboxMessageHandler;
import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.repository.RunRepository;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunAdmissionWorker implements OutboxMessageHandler {

    private static final String CONSUMER_NAME = "run-admission-worker";
    private static final String COMPUTE_RESOURCE_CLASS = "compute";
    private static final String INSUFFICIENT_QUOTA = "INSUFFICIENT_QUOTA";

    private final RunRepository runRepository;
    private final JobRepository jobRepository;
    private final QuotaAccountingService quotaAccountingService;
    private final EventConsumerDedupeService dedupeService;
    private final DomainEventAppender domainEventAppender;
    private final JobRunEventPublisher eventPublisher;

    @Override
    public String topic() {
        return EventTopics.RUN_SUBMITTED;
    }

    @Override
    public void handle(OutboxMessage message) {
        dedupeService.runOnce(CONSUMER_NAME, message.getEventId(), () -> admitRun(message));
    }

    private void admitRun(OutboxMessage message) {
        var runId = extractRunId(message);
        var run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run", runId));

        if (run.getStatus() != RunStatus.SUBMITTED) {
            log.debug("Skipping admission for run {} with status {}", run.getId(), run.getStatus());
            return;
        }

        var correlationId = extractCorrelationId(message);
        try {
            admitWithQuotaReservation(run, correlationId);
        } catch (InsufficientQuotaException ex) {
            rejectForInsufficientQuota(run, correlationId, ex);
        }
    }

    private void admitWithQuotaReservation(Run run, UUID correlationId) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var reservedMinutes = quotaAccountingService.reserveInitialLease(
                run.getUser().getId(),
                run,
                "Initial lease reserved during admission"
        );

        run.setStatus(RunStatus.QUEUED);
        run.setResourceClass(COMPUTE_RESOURCE_CLASS);
        run.setQueuedAt(now);
        run.setCurrentLeaseReservedMinutes(reservedMinutes);
        run.setActiveLeaseExpiresAt(now.plusMinutes(reservedMinutes));
        run.setLeaseSequence(1L);
        run.setLeaseSettled(false);
        runRepository.save(run);

        syncJobProjection(run);
        eventPublisher.runEvent("RunQueued", run, correlationId);
        log.debug("Admitted run {} with {} reserved minutes", run.getId(), reservedMinutes);
    }

    private void rejectForInsufficientQuota(Run run, UUID correlationId, InsufficientQuotaException ex) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        run.setStatus(RunStatus.FAILED);
        run.setTerminalReason(INSUFFICIENT_QUOTA);
        run.setProcessFinishedAt(now);
        run.setLeaseSettled(true);
        runRepository.save(run);

        syncJobProjection(run);
        appendQuotaRejected(run, correlationId, ex.getMessage(), now);
        eventPublisher.runEvent("RunFailed", run, correlationId);
        log.debug("Rejected run {} for insufficient quota", run.getId());
    }

    private void syncJobProjection(Run run) {
        Job job = run.getJob();
        job.setStatus(run.getStatus());
        job.setCurrentRun(run);
        job.setQueuedAt(run.getQueuedAt());
        job.setStartedAt(run.getStartedAt());
        job.setFinishedAt(run.getProcessFinishedAt());
        job.setActiveLeaseExpiresAt(run.getActiveLeaseExpiresAt());
        job.setCurrentLeaseReservedMinutes(run.getCurrentLeaseReservedMinutes());
        job.setLeaseSequence(run.getLeaseSequence());
        job.setLeaseSettled(run.getLeaseSettled());
        job.setTotalConsumedMinutes(run.getTotalConsumedMinutes());
        jobRepository.save(job);
    }

    private void appendQuotaRejected(Run run, UUID correlationId, String reason, OffsetDateTime occurredAt) {
        var bounds = quotaAccountingService.resolveMonthlyBounds(occurredAt);
        domainEventAppender.append(new DomainEventAppendRequest(
                "QuotaRejected",
                AggregateIds.QUOTA_BALANCE,
                AggregateIds.quotaBalance(run.getUser().getId(), bounds.start(), COMPUTE_RESOURCE_CLASS),
                Map.of(
                        "userId", run.getUser().getId(),
                        "jobId", run.getJob().getId(),
                        "runId", run.getId(),
                        "factType", INSUFFICIENT_QUOTA,
                        "reason", reason == null ? "" : reason
                ),
                Map.of(),
                "backend",
                "SYSTEM",
                CONSUMER_NAME,
                run.getUser().getId(),
                run.getJob().getId(),
                null,
                correlationId,
                null,
                occurredAt,
                1,
                List.of(EventTopics.QUOTA_REJECTED)
        ));
    }

    private UUID extractRunId(OutboxMessage message) {
        var rawRunId = message.getPayload().path("runId").asText(null);
        if (rawRunId == null || rawRunId.isBlank()) {
            throw new IllegalArgumentException(EventTopics.RUN_SUBMITTED + " payload is missing runId");
        }
        return UUID.fromString(rawRunId);
    }

    private UUID extractCorrelationId(OutboxMessage message) {
        var rawCorrelationId = message.getHeaders().path("correlation_id").asText(null);
        if (rawCorrelationId == null || rawCorrelationId.isBlank()) {
            return UUID.randomUUID();
        }
        return UUID.fromString(rawCorrelationId);
    }
}
