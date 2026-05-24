package com.pcrm.backend.jobs.service;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nomad.NomadJobControlClient;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.nomad.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LeaseWorker {

    private static final int MAX_ERROR_LENGTH = 4000;
    private static final String LEASE_RENEWAL_FAILED = "LEASE_RENEWAL_FAILED";
    private static final String LEASE_EXPIRED = "LEASE_EXPIRED";
    private static final List<JobStatus> LEASE_ENFORCED_STATUSES = List.of(
            JobStatus.QUEUED,
            JobStatus.DISPATCHING,
            JobStatus.SCHEDULING,
            JobStatus.RUNNING,
            JobStatus.FINALIZING
    );

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final QuotaAccountingService quotaAccountingService;
    private final NomadJobControlClient nomadJobControlClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.scheduler.lease.safety-window-ms:120000}")
    private long safetyWindowMs;

    @Value("${app.scheduler.lease.batch-size:50}")
    private int batchSize;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileActiveLeasesOnStartup() {
        enforceDueLeases("startup");
    }

    @Scheduled(fixedDelayString = "${app.scheduler.lease.interval-ms:30000}")
    public void enforceDueLeases() {
        enforceDueLeases("scheduled");
    }

    void enforceDueLeases(String trigger) {
        if (batchSize <= 0) {
            return;
        }

        var threshold = OffsetDateTime.now(ZoneOffset.UTC)
                .plus(Duration.ofMillis(Math.max(0L, safetyWindowMs)));
        var candidates = transactionTemplate.execute(_ -> jobRepository.findLeaseEnforcementCandidatesForUpdate(
                LEASE_ENFORCED_STATUSES,
                threshold,
                PageRequest.of(0, batchSize)
        ));

        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        log.debug("Lease {} scan found {} candidate jobs", trigger, candidates.size());
        candidates.stream()
                .map(Job::getId)
                .forEach(this::enforceJobLease);
    }

    private void enforceJobLease(UUID jobId) {
        LeaseDecision stopRequest;
        try {
            stopRequest = transactionTemplate.execute(_ -> renewOrPrepareStop(jobId));
        } catch (RuntimeException ex) {
            stopRequest = transactionTemplate.execute(_ ->
                    prepareStopAfterRenewalFailure(jobId, summarize(ex))
            );
        }

        if (stopRequest == null || stopRequest.type() == LeaseDecisionType.NONE) {
            return;
        }

        var nomadJobId = stopRequest.nomadJobId();
        var reason = stopRequest.reason();
        var correlationId = stopRequest.correlationId();

        if (nomadJobId == null || nomadJobId.isBlank()) {
            transactionTemplate.executeWithoutResult(_ ->
                    markJobTimedOut(jobId, reason, correlationId)
            );
            return;
        }

        try {
            nomadJobControlClient.stopJob(nomadJobId);
            transactionTemplate.executeWithoutResult(_ ->
                    markJobTimedOut(jobId, reason, correlationId)
            );
        } catch (Exception ex) {
            var stopFailureReason = summarize(ex);
            transactionTemplate.executeWithoutResult(_ ->
                    recordStopFailure(jobId, stopFailureReason, correlationId)
            );
            log.error("Failed to stop Nomad job {} for job {}", nomadJobId, jobId, ex);
        }
    }

    private LeaseDecision renewOrPrepareStop(UUID jobId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || !shouldEnforce(job)) {
            return LeaseDecision.none();
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var correlationId = UUID.randomUUID();
        if (!job.getActiveLeaseExpiresAt().isAfter(now)) {
            return prepareStop(job, LEASE_EXPIRED, now, correlationId);
        }

        renewLease(job, now, correlationId);
        return LeaseDecision.none();
    }

    private LeaseDecision prepareStopAfterRenewalFailure(UUID jobId, String reason) {
        var job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || !shouldEnforce(job)) {
            return LeaseDecision.none();
        }
        return prepareStop(job, reason, OffsetDateTime.now(ZoneOffset.UTC), UUID.randomUUID());
    }

    private void renewLease(Job job, OffsetDateTime now, UUID correlationId) {
        var nextLeaseExpiresAt = job.getActiveLeaseExpiresAt()
                .plusMinutes(quotaAccountingService.getLeaseMinutes());
        var additionalReservedMinutes = quotaAccountingService.reserveAdditionalLease(
                job.getProfile().getId(),
                job,
                nextLeaseExpiresAt,
                "Lease renewed before expiry"
        );

        jobStateMachine.markLeaseRenewed(job, nextLeaseExpiresAt, additionalReservedMinutes);
        log.debug("Renewed lease for job {} until {}", job.getId(), nextLeaseExpiresAt);
    }

    private LeaseDecision prepareStop(Job job, String reason, OffsetDateTime now, UUID correlationId) {
        jobStateMachine.markLeaseStopRequested(job, now, truncate(reason, MAX_ERROR_LENGTH));
        return LeaseDecision.stop(job.getId().toString(), reason, correlationId);
    }

    private void markJobTimedOut(UUID jobId, String reason, UUID correlationId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || jobStateMachine.isTerminal(job.getStatus())) {
            return;
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        settleCurrentLeaseIfNeeded(job, now, "Lease settled after enforcement timeout");
        jobStateMachine.markTimedOut(
                job,
                now,
                reason == null || reason.isBlank() ? LEASE_RENEWAL_FAILED : truncate(reason, 120)
        );
        log.info("Marked job {} as TIMED_OUT after lease enforcement", job.getId());
    }

    private void recordStopFailure(UUID jobId, String reason, UUID correlationId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || jobStateMachine.isTerminal(job.getStatus())) {
            return;
        }

        jobStateMachine.recordLeaseStopFailure(
                job,
                OffsetDateTime.now(ZoneOffset.UTC),
                truncate(reason, MAX_ERROR_LENGTH)
        );
    }

    private boolean shouldEnforce(Job job) {
        return LEASE_ENFORCED_STATUSES.contains(job.getStatus())
                && !Boolean.TRUE.equals(job.getLeaseSettled())
                && job.getActiveLeaseExpiresAt() != null
                && job.getCurrentLeaseReservedMinutes() != null
                && job.getCurrentLeaseReservedMinutes() > 0;
    }

    private void settleCurrentLeaseIfNeeded(Job job, OffsetDateTime now, String reason) {
        if (Boolean.TRUE.equals(job.getLeaseSettled())) {
            return;
        }

        long reservedMinutes = Math.max(0L, job.getCurrentLeaseReservedMinutes());
        long consumedMinutes = calculateConsumedMinutes(job, now, reservedMinutes);

        quotaAccountingService.settleLeaseMinutes(job, reservedMinutes, consumedMinutes, reason);
        jobStateMachine.markCurrentLeaseSettled(job, consumedMinutes);
    }

    private long calculateConsumedMinutes(Job job, OffsetDateTime now, long reservedMinutes) {
        if (reservedMinutes <= 0 || job.getStartedAt() == null) {
            return 0L;
        }

        long totalUnits = Math.max(1L, quotaAccountingService.getJobTotalUnits(job));
        long reservedRealMinutes = Math.max(1L, (reservedMinutes + totalUnits - 1L) / totalUnits);
        var leaseStart = job.getActiveLeaseExpiresAt() != null
                ? job.getActiveLeaseExpiresAt().minusMinutes(reservedRealMinutes)
                : job.getStartedAt();
        var effectiveStart = leaseStart.isAfter(job.getStartedAt()) ? leaseStart : job.getStartedAt();
        long elapsedSeconds = Math.max(0L, Duration.between(effectiveStart, now).getSeconds());
        long roundedUpMinutes = (elapsedSeconds + 59L) / 60L;
        return Math.min(reservedMinutes, roundedUpMinutes * totalUnits);
    }

    private String summarize(Exception ex) {
        var message = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
        return truncate(message, MAX_ERROR_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private enum LeaseDecisionType {
        NONE,
        STOP
    }

    private record LeaseDecision(
            LeaseDecisionType type,
            String nomadJobId,
            String reason,
            UUID correlationId
    ) {
        private static LeaseDecision none() {
            return new LeaseDecision(LeaseDecisionType.NONE, null, null, null);
        }

        private static LeaseDecision stop(String nomadJobId, String reason, UUID correlationId) {
            return new LeaseDecision(LeaseDecisionType.STOP, nomadJobId, reason, correlationId);
        }
    }
}
