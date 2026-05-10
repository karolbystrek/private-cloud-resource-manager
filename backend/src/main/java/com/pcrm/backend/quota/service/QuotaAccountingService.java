package com.pcrm.backend.quota.service;

import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaAccountingService {

    private final ProfileRepository profileRepository;
    private final QuotaWindowRepository quotaWindowRepository;
    private final QuotaLedgerRepository quotaLedgerRepository;
    private final QuotaPolicyResolverService quotaPolicyResolverService;

    @Value("${app.quota.lease-minutes:15}")
    private long leaseMinutes;

    @Transactional
    public long reserveInitialLease(UUID userId, Job job, String reason) {
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));
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
        quotaLedgerRepository.save(buildLedgerEntry(profile, job, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_RESERVE, leaseMinutes, reason, now));
        return leaseMinutes;
    }

    @Transactional
    public void refundLeaseReservation(Job job, long minutes, String reason) {
        settleLeaseMinutes(job, minutes, 0L, reason);
    }

    @Transactional
    public void settleLeaseMinutes(Job job, long reservedMinutes, long consumedMinutes, String reason) {
        var profile = profileRepository.findById(job.getProfile().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profile", job.getProfile().getId()));

        var referenceTime = job.getQueuedAt() != null ? job.getQueuedAt() : job.getCreatedAt();
        var referencePoint = referenceTime != null ? referenceTime : OffsetDateTime.now(ZoneOffset.UTC);
        var bounds = resolveBounds(referencePoint);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, referencePoint);
        var window = getOrCreateWindowForUpdate(profile, bounds, effectivePolicy, now);

        long releasableMinutes = Math.min(Math.max(0L, reservedMinutes), window.getReservedMinutes());
        long consumed = Math.min(Math.max(0L, consumedMinutes), releasableMinutes);
        long refunded = releasableMinutes - consumed;

        window.setReservedMinutes(window.getReservedMinutes() - releasableMinutes);
        window.setConsumedMinutes(window.getConsumedMinutes() + consumed);
        bumpVersionAndUpdatedAt(window, now);
        quotaWindowRepository.save(window);

        if (consumed > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(profile, job, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_CONSUME, consumed, reason, now));
        }
        if (refunded > 0) {
            quotaLedgerRepository.save(buildLedgerEntry(profile, job, job.getLeaseSequence(), QuotaLedgerEntryType.LEASE_REFUND, refunded, reason, now));
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
            long leaseSequence,
            QuotaLedgerEntryType type,
            long minutes,
            String reason,
            OffsetDateTime createdAt
    ) {
        return QuotaLedgerEntry.builder()
                .profile(profile)
                .job(job)
                .leaseSequence(Math.max(0L, leaseSequence))
                .entryType(type)
                .minutes(Math.max(0L, minutes))
                .reason(reason)
                .createdAt(createdAt)
                .build();
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
