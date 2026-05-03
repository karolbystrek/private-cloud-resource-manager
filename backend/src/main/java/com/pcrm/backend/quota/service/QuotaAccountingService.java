package com.pcrm.backend.quota.service;

import com.pcrm.backend.events.service.AggregateIds;
import com.pcrm.backend.events.service.DomainEventAppendRequest;
import com.pcrm.backend.events.service.DomainEventAppender;
import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.quota.domain.QuotaLedgerEntry;
import com.pcrm.backend.quota.domain.QuotaLedgerEntryType;
import com.pcrm.backend.quota.domain.QuotaWindow;
import com.pcrm.backend.quota.dto.QuotaLedgerEntryResponse;
import com.pcrm.backend.quota.dto.QuotaSummaryResponse;
import com.pcrm.backend.quota.repository.QuotaLedgerRepository;
import com.pcrm.backend.quota.repository.QuotaWindowRepository;
import com.pcrm.backend.user.Profile;
import com.pcrm.backend.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaAccountingService {

    private final ProfileRepository profileRepository;
    private final QuotaWindowRepository quotaWindowRepository;
    private final QuotaLedgerRepository quotaLedgerRepository;
    private final QuotaPolicyResolverService quotaPolicyResolverService;
    private final DomainEventAppender domainEventAppender;

    @Value("${app.quota.lease-minutes:15}")
    private long leaseMinutes;

    @Transactional
    public long reserveInitialLease(UUID profileId, Job job, String reason) {
        if (job.getCurrentRun() != null) {
            return reserveInitialLease(profileId, job.getCurrentRun(), reason);
        }

        var profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, now);
        var bounds = resolveBounds(now);
        var window = getOrCreateWindowForUpdate(profile, bounds, effectivePolicy, now);

        var remaining = calculateRemainingMinutes(window);
        if (!effectivePolicy.unlimited() && remaining < leaseMinutes) {
            throw new InsufficientQuotaException(remaining, leaseMinutes);
        }

        window.setReservedMinutes(window.getReservedMinutes() + leaseMinutes);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);
        quotaLedgerRepository.save(buildLedgerEntry(profile, job, null, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now));
        appendQuotaEvent("QuotaReserved", profile, job, null, QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now);
        return leaseMinutes;
    }

    @Transactional
    public long reserveInitialLease(UUID profileId, Run run, String reason) {
        var profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("User", profileId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, now);
        var bounds = resolveBounds(now);
        var window = getOrCreateWindowForUpdate(profile, bounds, effectivePolicy, now);

        var remaining = calculateRemainingMinutes(window);
        if (!effectivePolicy.unlimited() && remaining < leaseMinutes) {
            throw new InsufficientQuotaException(remaining, leaseMinutes);
        }

        window.setReservedMinutes(window.getReservedMinutes() + leaseMinutes);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);
        quotaLedgerRepository.save(buildLedgerEntry(profile, run.getJob(), run, run.getLeaseSequence(), QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now));
        appendQuotaEvent("QuotaReserved", profile, run.getJob(), run, QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now);
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

        var profile = profileRepository.findById(job.getProfile().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profile", job.getProfile().getId()));

        var referenceTime = job.getQueuedAt() != null ? job.getQueuedAt() : job.getCreatedAt();
        var referencePoint = referenceTime != null ? referenceTime : OffsetDateTime.now(ZoneOffset.UTC);
        var bounds = resolveBounds(referencePoint);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, referencePoint);
        var window = getOrCreateWindowForUpdate(profile, bounds, effectivePolicy, now);

        long releasableMinutes = Math.clamp(reservedMinutes, 0L, window.getReservedMinutes());
        long consumed = Math.clamp(consumedMinutes, 0L, releasableMinutes);
        long refunded = releasableMinutes - consumed;

        window.setReservedMinutes(window.getReservedMinutes() - releasableMinutes);
        window.setConsumedMinutes(window.getConsumedMinutes() + consumed);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);

        if (consumed > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(profile, job, null, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now));
            appendQuotaEvent("QuotaConsumed", profile, job, null, QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now);
        }
        if (refunded > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(profile, job, null, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now));
            appendQuotaEvent("QuotaReleased", profile, job, null, QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now);
        }
    }

    @Transactional
    public void settleLeaseMinutes(Run run, long reservedMinutes, long consumedMinutes, String reason) {
        var profile = profileRepository.findById(run.getProfile().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", run.getProfile().getId()));

        var referenceTime = run.getQueuedAt() != null ? run.getQueuedAt() : run.getCreatedAt();
        var referencePoint = referenceTime != null ? referenceTime : OffsetDateTime.now(ZoneOffset.UTC);
        var bounds = resolveBounds(referencePoint);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, referencePoint);
        var window = getOrCreateWindowForUpdate(profile, bounds, effectivePolicy, now);

        long releasableMinutes = Math.clamp(reservedMinutes, 0L, window.getReservedMinutes());
        long consumed = Math.clamp(consumedMinutes, 0L, releasableMinutes);
        long refunded = releasableMinutes - consumed;

        window.setReservedMinutes(window.getReservedMinutes() - releasableMinutes);
        window.setConsumedMinutes(window.getConsumedMinutes() + consumed);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);

        if (consumed > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(profile, run.getJob(), run, run.getLeaseSequence(), QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now));
            appendQuotaEvent("QuotaConsumed", profile, run.getJob(), run, QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now);
        }
        if (refunded > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(profile, run.getJob(), run, run.getLeaseSequence(), QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now));
            appendQuotaEvent("QuotaReleased", profile, run.getJob(), run, QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now);
        }
    }

    @Transactional(readOnly = true)
    public QuotaFairnessSnapshot loadFairnessSnapshot(UUID userId, OffsetDateTime at) {
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, at);
        var bounds = resolveBounds(at);
        var existingWindow = quotaWindowRepository.findByProfile_IdAndWindowStart(userId, bounds.start());

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
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, now);
        var bounds = resolveBounds(now);

        var existingWindow = quotaWindowRepository.findByProfile_IdAndWindowStart(userId, bounds.start());
        long allocated = existingWindow.map(QuotaWindow::getAllocatedMinutes).orElse(effectivePolicy.monthlyMinutes());
        long reserved = existingWindow.map(QuotaWindow::getReservedMinutes).orElse(0L);
        long consumed = existingWindow.map(QuotaWindow::getConsumedMinutes).orElse(0L);

        long remaining = effectivePolicy.unlimited()
                ? Long.MAX_VALUE
                : Math.max(0L, allocated - reserved - consumed);

        return new QuotaSummaryResponse(
                profile.getRole(),
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
                .findByProfile_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(userId, start, end)
                .stream()
                .map(QuotaLedgerEntryResponse::from)
                .toList();
    }

    public long getLeaseMinutes() {
        return leaseMinutes;
    }

    private QuotaWindow getOrCreateWindowForUpdate(
            Profile profile,
            QuotaWindowBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        return quotaWindowRepository.findByUserIdAndWindowStartForUpdate(profile.getId(), bounds.start())
                .orElseGet(() -> createAndLockWindow(profile, bounds, effectivePolicy, now));
    }

    private QuotaWindow createAndLockWindow(
            Profile profile,
            QuotaWindowBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        var newWindow = QuotaWindow.builder()
                .profile(profile)
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
                    profile,
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

        return quotaWindowRepository.findByUserIdAndWindowStartForUpdate(profile.getId(), bounds.start())
                .orElseThrow(() -> new ResourceNotFoundException("QuotaWindow", "userId", profile.getId().toString()));
    }

    private QuotaLedgerEntry buildLedgerEntry(
            Profile profile,
            Job job,
            Run run,
            long leaseSequence,
            QuotaLedgerEntryType type,
            long minutes,
            String reason,
            OffsetDateTime createdAt
    ) {
        return QuotaLedgerEntry.builder()
                .profile(profile)
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
            Profile profile,
            Job job,
            Run run,
            QuotaLedgerEntryType entryType,
            long minutes,
            String reason,
            OffsetDateTime occurredAt
    ) {
        var reference = occurredAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : occurredAt;
        var aggregateId = AggregateIds.quotaBalance(profile.getId(), resolveBounds(reference).start(), "compute");
        domainEventAppender.append(new DomainEventAppendRequest(
                eventType,
                AggregateIds.QUOTA_BALANCE,
                aggregateId,
                Map.of(
                        "profileId", profile.getId(),
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
                profile.getId(),
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
