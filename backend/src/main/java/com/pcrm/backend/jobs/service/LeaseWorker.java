package com.pcrm.backend.jobs.service;

import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.repository.RunRepository;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.nomad.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class LeaseWorker {

    private static final int MAX_ERROR_LENGTH = 4000;
    private static final String LEASE_RENEWAL_FAILED = "LEASE_RENEWAL_FAILED";
    private static final String LEASE_EXPIRED = "LEASE_EXPIRED";
    private static final List<RunStatus> LEASE_ENFORCED_STATUSES = List.of(
            RunStatus.QUEUED,
            RunStatus.DISPATCHING,
            RunStatus.SCHEDULING,
            RunStatus.RUNNING,
            RunStatus.FINALIZING
    );

    private final RunRepository runRepository;
    private final RunStateMachine runStateMachine;
    private final QuotaAccountingService quotaAccountingService;
    private final NomadJobControlClient nomadJobControlClient;
    private final JobRunEventPublisher eventPublisher;
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
        var candidates = transactionTemplate.execute(_ -> runRepository.findLeaseEnforcementCandidatesForUpdate(
                LEASE_ENFORCED_STATUSES,
                threshold,
                PageRequest.of(0, batchSize)
        ));

        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        log.debug("Lease {} scan found {} candidate runs", trigger, candidates.size());
        candidates.stream()
                .map(Run::getId)
                .forEach(this::enforceRunLease);
    }

    private void enforceRunLease(UUID runId) {
        LeaseDecision stopRequest;
        try {
            stopRequest = transactionTemplate.execute(_ -> renewOrPrepareStop(runId));
        } catch (RuntimeException ex) {
            stopRequest = transactionTemplate.execute(_ ->
                    prepareStopAfterRenewalFailure(runId, summarize(ex))
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
                    markRunTimedOut(runId, reason, correlationId)
            );
            return;
        }

        try {
            nomadJobControlClient.stopJob(nomadJobId);
            transactionTemplate.executeWithoutResult(_ ->
                    markRunTimedOut(runId, reason, correlationId)
            );
        } catch (Exception ex) {
            var stopFailureReason = summarize(ex);
            transactionTemplate.executeWithoutResult(_ ->
                    recordStopFailure(runId, stopFailureReason, correlationId)
            );
            log.error("Failed to stop Nomad job {} for run {}", nomadJobId, runId, ex);
        }
    }

    private LeaseDecision renewOrPrepareStop(UUID runId) {
        var run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || !shouldEnforce(run)) {
            return LeaseDecision.none();
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var correlationId = UUID.randomUUID();
        if (!run.getActiveLeaseExpiresAt().isAfter(now)) {
            return prepareStop(run, LEASE_EXPIRED, now, correlationId);
        }

        renewLease(run, now, correlationId);
        return LeaseDecision.none();
    }

    private LeaseDecision prepareStopAfterRenewalFailure(UUID runId, String reason) {
        var run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || !shouldEnforce(run)) {
            return LeaseDecision.none();
        }
        return prepareStop(run, reason, OffsetDateTime.now(ZoneOffset.UTC), UUID.randomUUID());
    }

    private void renewLease(Run run, OffsetDateTime now, UUID correlationId) {
        var nextLeaseExpiresAt = run.getActiveLeaseExpiresAt()
                .plusMinutes(quotaAccountingService.getLeaseMinutes());
        var additionalReservedMinutes = quotaAccountingService.reserveAdditionalLease(
                run.getProfile().getId(),
                run,
                nextLeaseExpiresAt,
                "Lease renewed before expiry"
        );

        runStateMachine.markLeaseRenewed(run, nextLeaseExpiresAt, additionalReservedMinutes);
        eventPublisher.runEvent(
                "RunLeaseRenewed",
                run,
                Map.of(
                        "additionalReservedMinutes", additionalReservedMinutes,
                        "activeLeaseExpiresAt", nextLeaseExpiresAt,
                        "renewedAt", now
                ),
                "backend",
                correlationId
        );
        log.debug("Renewed lease for run {} until {}", run.getId(), nextLeaseExpiresAt);
    }

    private LeaseDecision prepareStop(Run run, String reason, OffsetDateTime now, UUID correlationId) {
        runStateMachine.markLeaseStopRequested(run, now, truncate(reason, MAX_ERROR_LENGTH));
        eventPublisher.runEvent(
                "RunLeaseStopRequested",
                run,
                Map.of(
                        "reason", reason == null ? "" : reason,
                        "activeLeaseExpiresAt", run.getActiveLeaseExpiresAt(),
                        "leaseStopRequestedAt", now
                ),
                "backend",
                correlationId
        );
        return LeaseDecision.stop(run.getNomadJobId(), reason, correlationId);
    }

    private void markRunTimedOut(UUID runId, String reason, UUID correlationId) {
        var run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || runStateMachine.isTerminal(run.getStatus())) {
            return;
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        settleCurrentLeaseIfNeeded(run, now, "Lease settled after enforcement timeout");
        runStateMachine.markTimedOut(
                run,
                now,
                reason == null || reason.isBlank() ? LEASE_RENEWAL_FAILED : truncate(reason, 120)
        );
        eventPublisher.runEvent(
                "RunTimedOut",
                run,
                Map.of("reason", run.getTerminalReason()),
                "backend",
                correlationId
        );
        log.info("Marked run {} as TIMED_OUT after lease enforcement", run.getId());
    }

    private void recordStopFailure(UUID runId, String reason, UUID correlationId) {
        var run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || runStateMachine.isTerminal(run.getStatus())) {
            return;
        }

        runStateMachine.recordLeaseStopFailure(
                run,
                OffsetDateTime.now(ZoneOffset.UTC),
                truncate(reason, MAX_ERROR_LENGTH)
        );
        eventPublisher.runEvent(
                "RunLeaseStopFailed",
                run,
                Map.of("reason", reason == null ? "" : reason),
                "backend",
                correlationId
        );
    }

    private boolean shouldEnforce(Run run) {
        return LEASE_ENFORCED_STATUSES.contains(run.getStatus())
                && !Boolean.TRUE.equals(run.getLeaseSettled())
                && run.getActiveLeaseExpiresAt() != null
                && run.getCurrentLeaseReservedMinutes() != null
                && run.getCurrentLeaseReservedMinutes() > 0;
    }

    private void settleCurrentLeaseIfNeeded(Run run, OffsetDateTime now, String reason) {
        if (Boolean.TRUE.equals(run.getLeaseSettled())) {
            return;
        }

        long reservedMinutes = Math.max(0L, run.getCurrentLeaseReservedMinutes());
        long consumedMinutes = calculateConsumedMinutes(run, now, reservedMinutes);

        quotaAccountingService.settleLeaseMinutes(run, reservedMinutes, consumedMinutes, reason);
        runStateMachine.markCurrentLeaseSettled(run, consumedMinutes);
    }

    private long calculateConsumedMinutes(Run run, OffsetDateTime now, long reservedMinutes) {
        if (reservedMinutes <= 0 || run.getStartedAt() == null) {
            return 0L;
        }

        var leaseStart = run.getActiveLeaseExpiresAt() != null
                ? run.getActiveLeaseExpiresAt().minusMinutes(reservedMinutes)
                : run.getStartedAt();
        var effectiveStart = leaseStart.isAfter(run.getStartedAt()) ? leaseStart : run.getStartedAt();
        long elapsedSeconds = Math.max(0L, Duration.between(effectiveStart, now).getSeconds());
        long roundedUpMinutes = (elapsedSeconds + 59L) / 60L;
        return Math.min(reservedMinutes, roundedUpMinutes);
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
