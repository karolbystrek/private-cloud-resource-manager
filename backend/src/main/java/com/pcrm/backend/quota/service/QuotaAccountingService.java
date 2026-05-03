package com.pcrm.backend.quota.service;

import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.events.service.AggregateIds;
import com.pcrm.backend.events.service.DomainEventAppendRequest;
import com.pcrm.backend.events.service.DomainEventAppender;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.quota.domain.QuotaLedgerEntry;
import com.pcrm.backend.quota.domain.QuotaLedgerEntryType;
import com.pcrm.backend.quota.domain.QuotaWindow;
import com.pcrm.backend.quota.dto.QuotaLedgerEntryResponse;
import com.pcrm.backend.quota.dto.QuotaSummaryResponse;
import com.pcrm.backend.quota.repository.QuotaLedgerRepository;
import com.pcrm.backend.quota.repository.QuotaWindowRepository;
import com.pcrm.backend.user.User;
import com.pcrm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaAccountingService {

    private final UserRepository userRepository;
    private final QuotaWindowRepository quotaWindowRepository;
    private final QuotaLedgerRepository quotaLedgerRepository;
    private final QuotaPolicyResolverService quotaPolicyResolverService;
    private final DomainEventAppender domainEventAppender;

    @Value("${app.quota.lease-minutes:15}")
    private long leaseMinutes;

    @Transactional
    public long reserveInitialLease(UUID userId, Job job, String reason) {
        if (job.getCurrentRun() != null) {
            return reserveInitialLease(userId, job.getCurrentRun(), reason);
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, now);
        var bounds = resolveBounds(now);
        var window = getOrCreateWindowForUpdate(user, bounds, effectivePolicy, now);

        var remaining = calculateRemainingMinutes(window);
        if (!effectivePolicy.unlimited() && remaining < leaseMinutes) {
            throw new InsufficientQuotaException(remaining, leaseMinutes);
        }

        window.setReservedMinutes(window.getReservedMinutes() + leaseMinutes);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);
        quotaLedgerRepository.save(buildLedgerEntry(user, job, null, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now));
        appendQuotaEvent("QuotaReserved", user, job, null, QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now);
        return leaseMinutes;
    }

    @Transactional
    public long reserveInitialLease(UUID userId, Run run, String reason) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, now);
        var bounds = resolveBounds(now);
        var window = getOrCreateWindowForUpdate(user, bounds, effectivePolicy, now);

        var remaining = calculateRemainingMinutes(window);
        if (!effectivePolicy.unlimited() && remaining < leaseMinutes) {
            throw new InsufficientQuotaException(remaining, leaseMinutes);
        }

        window.setReservedMinutes(window.getReservedMinutes() + leaseMinutes);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);
        quotaLedgerRepository.save(buildLedgerEntry(user, run.getJob(), run, run.getLeaseSequence(), QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now));
        appendQuotaEvent("QuotaReserved", user, run.getJob(), run, QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now);
        return leaseMinutes;
    }

    @Transactional
    public void refundLeaseReservation(Job job, long minutes, String reason) {
        if (job.getCurrentRun() != null) {
            refundLeaseReservation(job.getCurrentRun(), minutes, reason);
            return;
        }
        settleLeaseMinutes(job, minutes, 0L, reason);
    }

    @Transactional
    public void refundLeaseReservation(Run run, long minutes, String reason) {
        settleLeaseMinutes(run, minutes, 0L, reason);
    }

    @Transactional
    public void settleLeaseMinutes(Job job, long reservedMinutes, long consumedMinutes, String reason) {
        if (job.getCurrentRun() != null) {
            settleLeaseMinutes(job.getCurrentRun(), reservedMinutes, consumedMinutes, reason);
            return;
        }

        var user = userRepository.findById(job.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", job.getUser().getId()));

        var referenceTime = job.getQueuedAt() != null ? job.getQueuedAt() : job.getCreatedAt();
        var referencePoint = referenceTime != null ? referenceTime : OffsetDateTime.now(ZoneOffset.UTC);
        var bounds = resolveBounds(referencePoint);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, referencePoint);
        var window = getOrCreateWindowForUpdate(user, bounds, effectivePolicy, now);

        long releasableMinutes = Math.min(Math.max(0L, reservedMinutes), window.getReservedMinutes());
        long consumed = Math.min(Math.max(0L, consumedMinutes), releasableMinutes);
        long refunded = releasableMinutes - consumed;

        window.setReservedMinutes(window.getReservedMinutes() - releasableMinutes);
        window.setConsumedMinutes(window.getConsumedMinutes() + consumed);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);

        if (consumed > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(user, job, null, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now));
            appendQuotaEvent("QuotaConsumed", user, job, null, QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now);
        }
        if (refunded > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(user, job, null, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now));
            appendQuotaEvent("QuotaReleased", user, job, null, QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now);
        }
    }

    @Transactional
    public void settleLeaseMinutes(Run run, long reservedMinutes, long consumedMinutes, String reason) {
        var user = userRepository.findById(run.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", run.getUser().getId()));

        var referenceTime = run.getQueuedAt() != null ? run.getQueuedAt() : run.getCreatedAt();
        var referencePoint = referenceTime != null ? referenceTime : OffsetDateTime.now(ZoneOffset.UTC);
        var bounds = resolveBounds(referencePoint);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, referencePoint);
        var window = getOrCreateWindowForUpdate(user, bounds, effectivePolicy, now);

        long releasableMinutes = Math.min(Math.max(0L, reservedMinutes), window.getReservedMinutes());
        long consumed = Math.min(Math.max(0L, consumedMinutes), releasableMinutes);
        long refunded = releasableMinutes - consumed;

        window.setReservedMinutes(window.getReservedMinutes() - releasableMinutes);
        window.setConsumedMinutes(window.getConsumedMinutes() + consumed);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);

        if (consumed > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(user, run.getJob(), run, run.getLeaseSequence(), QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now));
            appendQuotaEvent("QuotaConsumed", user, run.getJob(), run, QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now);
        }
        if (refunded > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(user, run.getJob(), run, run.getLeaseSequence(), QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now));
            appendQuotaEvent("QuotaReleased", user, run.getJob(), run, QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now);
        }
    }

    @Transactional(readOnly = true)
    public QuotaFairnessSnapshot loadFairnessSnapshot(UUID userId, OffsetDateTime at) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, at);
        var bounds = resolveBounds(at);
        var existingWindow = quotaWindowRepository.findByUser_IdAndWindowStart(userId, bounds.start());

        long allocatedMinutes = effectivePolicy.monthlyMinutes();
        long consumedMinutes = existingWindow.map(QuotaWindow::getConsumedMinutes).orElse(0L);

        return new QuotaFairnessSnapshot(
                allocatedMinutes,
                consumedMinutes,
                effectivePolicy.roleWeight(),
                effectivePolicy.unlimited()
        );
    }

    @Transactional(readOnly = true)
    public QuotaSummaryResponse getQuotaSummary(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, now);
        var bounds = resolveBounds(now);

        var existingWindow = quotaWindowRepository.findByUser_IdAndWindowStart(userId, bounds.start());
        long allocated = existingWindow.map(QuotaWindow::getAllocatedMinutes).orElse(effectivePolicy.monthlyMinutes());
        long reserved = existingWindow.map(QuotaWindow::getReservedMinutes).orElse(0L);
        long consumed = existingWindow.map(QuotaWindow::getConsumedMinutes).orElse(0L);

        long remaining = effectivePolicy.unlimited()
                ? Long.MAX_VALUE
                : Math.max(0L, allocated - reserved - consumed);

        return new QuotaSummaryResponse(
                user.getRole(),
                allocated,
                reserved,
                consumed,
                remaining,
                effectivePolicy.unlimited(),
                effectivePolicy.roleWeight(),
                bounds.start(),
                bounds.end(),
                bounds.end()
        );
    }

    @Transactional(readOnly = true)
    public List<QuotaLedgerEntryResponse> getQuotaLedger(UUID userId, YearMonth window) {
        var start = OffsetDateTime.of(window.getYear(), window.getMonthValue(), 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var end = start.plusMonths(1);

        return quotaLedgerRepository
                .findByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(userId, start, end)
                .stream()
                .map(QuotaLedgerEntryResponse::from)
                .toList();
    }

    public long getLeaseMinutes() {
        return leaseMinutes;
    }

    private QuotaWindow getOrCreateWindowForUpdate(
            User user,
            QuotaWindowBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        return quotaWindowRepository.findByUserIdAndWindowStartForUpdate(user.getId(), bounds.start())
                .orElseGet(() -> createAndLockWindow(user, bounds, effectivePolicy, now));
    }

    private QuotaWindow createAndLockWindow(
            User user,
            QuotaWindowBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        var newWindow = QuotaWindow.builder()
                .user(user)
                .windowStart(bounds.start())
                .windowEnd(bounds.end())
                .allocatedMinutes(effectivePolicy.monthlyMinutes())
                .reservedMinutes(0L)
                .consumedMinutes(0L)
                .updatedAt(now)
                .version(0L)
                .build();

        try {
            quotaWindowRepository.saveAndFlush(newWindow);
            quotaLedgerRepository.save(buildLedgerEntry(
                    user,
                    null,
                    null,
                    0L,
                    QuotaLedgerEntryType.WINDOW_ALLOCATION,
                    effectivePolicy.monthlyMinutes(),
                    "Monthly quota allocation",
                    now
            ));
        } catch (DataIntegrityViolationException ignored) {
        }

        return quotaWindowRepository.findByUserIdAndWindowStartForUpdate(user.getId(), bounds.start())
                .orElseThrow(() -> new ResourceNotFoundException("QuotaWindow", "userId", user.getId().toString()));
    }

    private QuotaLedgerEntry buildLedgerEntry(
            User user,
            Job job,
            Run run,
            long leaseSequence,
            QuotaLedgerEntryType type,
            long minutes,
            String reason,
            OffsetDateTime createdAt
    ) {
        return QuotaLedgerEntry.builder()
                .user(user)
                .job(job)
                .run(run)
                .leaseSequence(Math.max(0L, leaseSequence))
                .entryType(type)
                .minutes(Math.max(0L, minutes))
                .reason(reason)
                .createdAt(createdAt)
                .build();
    }

    private void appendQuotaEvent(
            String eventType,
            User user,
            Job job,
            Run run,
            QuotaLedgerEntryType entryType,
            long minutes,
            String reason,
            OffsetDateTime occurredAt
    ) {
        var reference = occurredAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : occurredAt;
        var aggregateId = AggregateIds.quotaBalance(user.getId(), resolveBounds(reference).start(), "compute");
        domainEventAppender.append(new DomainEventAppendRequest(
                eventType,
                AggregateIds.QUOTA_BALANCE,
                aggregateId,
                Map.of(
                        "userId", user.getId(),
                        "jobId", job == null ? "" : job.getId(),
                        "runId", run == null ? "" : run.getId(),
                        "entryType", entryType.name(),
                        "minutes", Math.max(0L, minutes),
                        "reason", reason == null ? "" : reason
                ),
                Map.of(),
                "backend",
                "SYSTEM",
                "quota-accounting",
                user.getId(),
                job == null ? null : job.getId(),
                null,
                UUID.randomUUID(),
                null,
                occurredAt,
                1,
                List.of(eventType)
        ));
    }

    private long calculateRemainingMinutes(QuotaWindow window) {
        return Math.max(0L, window.getAllocatedMinutes() - window.getReservedMinutes() - window.getConsumedMinutes());
    }

    private void bumpVersionAndUpdatedAt(QuotaWindow window, OffsetDateTime now) {
        window.setUpdatedAt(now);
        window.setVersion(window.getVersion() + 1L);
    }

    private QuotaWindowBounds resolveBounds(OffsetDateTime at) {
        var utc = at.withOffsetSameInstant(ZoneOffset.UTC);
        var start = OffsetDateTime.of(utc.getYear(), utc.getMonthValue(), 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var end = start.plusMonths(1);
        return new QuotaWindowBounds(start, end);
    }
}
