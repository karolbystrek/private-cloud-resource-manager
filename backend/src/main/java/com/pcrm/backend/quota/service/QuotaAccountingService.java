package com.pcrm.backend.quota.service;

import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.quota.domain.*;
import com.pcrm.backend.quota.dto.QuotaSummaryResponse;
import com.pcrm.backend.quota.dto.QuotaUsageLedgerEntryResponse;
import com.pcrm.backend.quota.dto.admin.AdminQuotaGrantRequest;
import com.pcrm.backend.quota.dto.admin.AdminQuotaGrantResponse;
import com.pcrm.backend.quota.repository.QuotaGrantRepository;
import com.pcrm.backend.quota.repository.QuotaReservationRepository;
import com.pcrm.backend.quota.repository.QuotaUsageLedgerRepository;
import com.pcrm.backend.quota.repository.UserQuotaBalanceCurrentRepository;
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
    private static final int DEFAULT_MULTIPLIER = 1;

    private final ProfileRepository profileRepository;
    private final QuotaGrantRepository quotaGrantRepository;
    private final QuotaReservationRepository quotaReservationRepository;
    private final QuotaUsageLedgerRepository quotaUsageLedgerRepository;
    private final UserQuotaBalanceCurrentRepository userQuotaBalanceCurrentRepository;
    private final QuotaPolicyResolverService quotaPolicyResolverService;
    @Value("${app.quota.lease-minutes:15}")
    private long leaseMinutes;

    @Transactional
    public long reserveInitialLease(UUID profileId, Job job, String reason) {
        var existingReservation = quotaReservationRepository
                .findByJobIdAndStatusForUpdate(job.getId(), QuotaReservationStatus.ACTIVE);
        if (existingReservation.isPresent()) {
            return existingReservation.get().getReservedComputeMinutes();
        }

        var profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, now);
        var bounds = resolveBounds(now);
        var balance = getOrCreateBalanceForUpdate(profile, bounds, effectivePolicy, now);
        var computeMinutes = normalizeComputeMinutes(leaseMinutes, DEFAULT_MULTIPLIER);

        if (!effectivePolicy.unlimited() && balance.getAvailableMinutes() < computeMinutes) {
            throw new InsufficientQuotaException(balance.getAvailableMinutes(), computeMinutes);
        }

        var reservation = quotaReservationRepository.save(QuotaReservation.builder()
                .profile(profile)
                .job(job)
                .intervalStart(bounds.start())
                .intervalEnd(bounds.end())
                .reservedComputeMinutes(computeMinutes)
                .consumedComputeMinutes(0L)
                .releasedComputeMinutes(0L)
                .expiresAt(now.plusMinutes(computeMinutes))
                .status(QuotaReservationStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build());
        reserveBalanceMinutes(balance, computeMinutes, now);
        return computeMinutes;
    }

    @Transactional
    public long reserveAdditionalLease(UUID profileId, Job job, OffsetDateTime nextLeaseExpiresAt, String reason) {
        var profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var referencePoint = resolveJobReferencePoint(job);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, referencePoint);
        var bounds = resolveBounds(referencePoint);
        var balance = getOrCreateBalanceForUpdate(profile, bounds, effectivePolicy, now);
        var reservation = findReservationForSettlement(job);

        if (reservation == null || reservation.getStatus() != QuotaReservationStatus.ACTIVE) {
            throw new IllegalStateException("Job %s has no active quota reservation to renew".formatted(job.getId()));
        }

        var computeMinutes = normalizeComputeMinutes(leaseMinutes, DEFAULT_MULTIPLIER);
        if (!effectivePolicy.unlimited() && balance.getAvailableMinutes() < computeMinutes) {
            throw new InsufficientQuotaException(balance.getAvailableMinutes(), computeMinutes);
        }

        reservation.setReservedComputeMinutes(reservation.getReservedComputeMinutes() + computeMinutes);
        reservation.setExpiresAt(nextLeaseExpiresAt);
        reservation.setUpdatedAt(now);
        quotaReservationRepository.save(reservation);

        reserveBalanceMinutes(balance, computeMinutes, now);
        return computeMinutes;
    }

    @Transactional
    public void refundLeaseReservation(Job job, long minutes, String reason) {
        settleLeaseMinutes(job, minutes, 0L, reason);
    }

    @Transactional
    public void settleLeaseMinutes(Job job, long reservedMinutes, long consumedMinutes, String reason) {
        var profile = profileRepository.findById(job.getProfile().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profile", job.getProfile().getId()));
        var referencePoint = resolveJobReferencePoint(job);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, referencePoint);
        var bounds = resolveBounds(referencePoint);
        var balance = getOrCreateBalanceForUpdate(profile, bounds, effectivePolicy, now);
        var reservation = findReservationForSettlement(job);

        if (reservation == null) {
            var settlement = settleBalanceMinutes(balance, Math.max(0L, reservedMinutes), Math.max(0L, consumedMinutes), now);
            appendSettlementFacts(profile, job, null, settlement, reason, now);
            return;
        }

        var unsettledReservationMinutes = Math.max(
                0L,
                reservation.getReservedComputeMinutes()
                        - reservation.getConsumedComputeMinutes()
                        - reservation.getReleasedComputeMinutes()
        );
        var releasableMinutes = Math.min(Math.max(0L, reservedMinutes), unsettledReservationMinutes);
        var consumed = Math.min(Math.max(0L, consumedMinutes), releasableMinutes);
        var released = releasableMinutes - consumed;

        reservation.setConsumedComputeMinutes(reservation.getConsumedComputeMinutes() + consumed);
        reservation.setReleasedComputeMinutes(reservation.getReleasedComputeMinutes() + released);
        reservation.setStatus(resolveReservationStatus(reservation));
        reservation.setUpdatedAt(now);
        quotaReservationRepository.save(reservation);

        var settlement = settleBalanceMinutes(balance, releasableMinutes, consumed, now);
        appendSettlementFacts(profile, job, reservation, settlement, reason, now);
    }

    @Transactional(readOnly = true)
    public QuotaFairnessSnapshot loadFairnessSnapshot(UUID profileId, OffsetDateTime at) {
        var profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, at);
        var bounds = resolveBounds(at);
        var existingBalance = userQuotaBalanceCurrentRepository.findByProfile_IdAndIntervalStart(profileId, bounds.start());

        long allocatedMinutes = existingBalance.map(UserQuotaBalanceCurrent::getGrantedMinutes)
                .orElse(effectivePolicy.monthlyMinutes());
        long consumedMinutes = existingBalance.map(UserQuotaBalanceCurrent::getConsumedMinutes).orElse(0L);

        return new QuotaFairnessSnapshot(
                allocatedMinutes,
                consumedMinutes,
                effectivePolicy.roleWeight(),
                effectivePolicy.unlimited()
        );
    }

    @Transactional
    public QuotaSummaryResponse getQuotaSummary(UUID profileId) {
        var profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, now);
        var bounds = resolveBounds(now);
        var balance = getOrCreateBalanceForUpdate(profile, bounds, effectivePolicy, now);

        long remaining = effectivePolicy.unlimited()
                ? Long.MAX_VALUE
                : balance.getAvailableMinutes();

        return new QuotaSummaryResponse(
                profile.getRole(),
                balance.getGrantedMinutes(),
                balance.getReservedMinutes(),
                balance.getConsumedMinutes(),
                remaining,
                effectivePolicy.unlimited(),
                effectivePolicy.roleWeight(),
                bounds.start(),
                bounds.end(),
                bounds.end()
        );
    }

    @Transactional(readOnly = true)
    public List<QuotaUsageLedgerEntryResponse> getQuotaUsageLedger(UUID profileId, YearMonth window) {
        var start = OffsetDateTime.of(window.getYear(), window.getMonthValue(), 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var end = start.plusMonths(1);
        return quotaUsageLedgerRepository
                .findByProfile_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(profileId, start, end)
                .stream()
                .map(QuotaUsageLedgerEntryResponse::from)
                .toList();
    }

    @Transactional
    public AdminQuotaGrantResponse addAdminGrant(
            UUID actorId,
            AdminQuotaGrantRequest request,
            QuotaIntervalBounds bounds,
            String idempotencyKey
    ) {
        var profile = profileRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        var actor = profileRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(profile, bounds.start());
        var balance = getOrCreateBalanceForUpdate(profile, bounds, effectivePolicy, now);
        var minutes = Math.max(0L, request.minutes());

        var grant = quotaGrantRepository.save(QuotaGrant.builder()
                .profile(profile)
                .intervalStart(bounds.start())
                .intervalEnd(bounds.end())
                .grantType(QuotaGrantType.ADMIN_BONUS)
                .minutes(minutes)
                .remainingMinutes(minutes)
                .status(QuotaGrantStatus.ACTIVE)
                .actor(actor)
                .reason(request.reason())
                .createdAt(now)
                .updatedAt(now)
                .build());

        balance.setGrantedMinutes(balance.getGrantedMinutes() + minutes);
        recalculateAvailable(balance);
        bumpVersionAndUpdatedAt(balance, now);
        userQuotaBalanceCurrentRepository.save(balance);

        return AdminQuotaGrantResponse.from(
                grant,
                balance.getGrantedMinutes(),
                balance.getReservedMinutes(),
                balance.getConsumedMinutes(),
                balance.getAvailableMinutes()
        );
    }

    public long getLeaseMinutes() {
        return leaseMinutes;
    }

    public QuotaIntervalBounds resolveMonthlyBounds(OffsetDateTime at) {
        return resolveBounds(at);
    }

    private UserQuotaBalanceCurrent getOrCreateBalanceForUpdate(
            Profile profile,
            QuotaIntervalBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        return userQuotaBalanceCurrentRepository.findByProfileIdAndIntervalStartForUpdate(profile.getId(), bounds.start())
                .orElseGet(() -> createAndLockBalance(profile, bounds, effectivePolicy, now));
    }

    private UserQuotaBalanceCurrent createAndLockBalance(
            Profile profile,
            QuotaIntervalBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        var monthlyMinutes = Math.max(0L, effectivePolicy.monthlyMinutes());
        var newBalance = UserQuotaBalanceCurrent.builder()
                .profile(profile)
                .intervalStart(bounds.start())
                .intervalEnd(bounds.end())
                .grantedMinutes(monthlyMinutes)
                .reservedMinutes(0L)
                .consumedMinutes(0L)
                .availableMinutes(monthlyMinutes)
                .updatedAt(now)
                .version(0L)
                .build();

        try {
            userQuotaBalanceCurrentRepository.saveAndFlush(newBalance);
            if (monthlyMinutes > 0) {
                var grant = quotaGrantRepository.save(QuotaGrant.builder()
                        .profile(profile)
                        .intervalStart(bounds.start())
                        .intervalEnd(bounds.end())
                        .grantType(QuotaGrantType.ROLE_GRANT)
                        .minutes(monthlyMinutes)
                        .remainingMinutes(monthlyMinutes)
                        .status(QuotaGrantStatus.ACTIVE)
                        .reason("Monthly role quota grant")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
            }
        } catch (DataIntegrityViolationException ignored) {
        }

        return userQuotaBalanceCurrentRepository.findByProfileIdAndIntervalStartForUpdate(profile.getId(), bounds.start())
                .orElseThrow(() -> new ResourceNotFoundException("UserQuotaBalanceCurrent", "profileId", profile.getId().toString()));
    }

    private void reserveBalanceMinutes(UserQuotaBalanceCurrent balance, long minutes, OffsetDateTime now) {
        balance.setReservedMinutes(balance.getReservedMinutes() + minutes);
        recalculateAvailable(balance);
        bumpVersionAndUpdatedAt(balance, now);
        userQuotaBalanceCurrentRepository.save(balance);
    }

    private BalanceSettlement settleBalanceMinutes(
            UserQuotaBalanceCurrent balance,
            long reservedMinutes,
            long consumedMinutes,
            OffsetDateTime now
    ) {
        long releasedReservedMinutes = Math.min(reservedMinutes, balance.getReservedMinutes());
        long consumed = Math.min(consumedMinutes, releasedReservedMinutes);
        long refunded = releasedReservedMinutes - consumed;

        balance.setReservedMinutes(balance.getReservedMinutes() - releasedReservedMinutes);
        balance.setConsumedMinutes(balance.getConsumedMinutes() + consumed);
        recalculateAvailable(balance);
        bumpVersionAndUpdatedAt(balance, now);
        userQuotaBalanceCurrentRepository.save(balance);

        return new BalanceSettlement(releasedReservedMinutes, consumed, refunded);
    }

    private QuotaReservation findReservationForSettlement(Job job) {
        return quotaReservationRepository
                .findByJobIdAndStatusForUpdate(job.getId(), QuotaReservationStatus.ACTIVE)
                .orElse(null);
    }

    private QuotaReservationStatus resolveReservationStatus(QuotaReservation reservation) {
        var settled = reservation.getConsumedComputeMinutes() + reservation.getReleasedComputeMinutes();
        if (settled < reservation.getReservedComputeMinutes()) {
            return QuotaReservationStatus.ACTIVE;
        }
        if (reservation.getConsumedComputeMinutes().equals(reservation.getReservedComputeMinutes())) {
            return QuotaReservationStatus.CONSUMED;
        }
        return QuotaReservationStatus.RELEASED;
    }

    private void appendSettlementFacts(
            Profile profile,
            Job job,
            QuotaReservation reservation,
            BalanceSettlement settlement,
            String reason,
            OffsetDateTime now
    ) {
        if (settlement.consumedMinutes() > 0) {
            quotaUsageLedgerRepository.save(buildUsageLedgerEntry(
                    profile,
                    job,
                    reservation,
                    QuotaUsageLedgerEntryType.USAGE_DEBITED,
                    settlement.consumedMinutes(),
                    reason,
                    now
            ));
        }
        if (settlement.refundedMinutes() > 0) {
            quotaUsageLedgerRepository.save(buildUsageLedgerEntry(
                    profile,
                    job,
                    reservation,
                    QuotaUsageLedgerEntryType.USAGE_RELEASED,
                    settlement.refundedMinutes(),
                    reason,
                    now
            ));
        }
    }

    private QuotaUsageLedgerEntry buildUsageLedgerEntry(
            Profile profile,
            Job job,
            QuotaReservation reservation,
            QuotaUsageLedgerEntryType type,
            long computeMinutes,
            String reason,
            OffsetDateTime createdAt
    ) {
        return QuotaUsageLedgerEntry.builder()
                .profile(profile)
                .job(job)
                .quotaReservation(reservation)
                .entryType(type)
                .rawRuntimeSeconds(null)
                .computeMinutes(Math.max(0L, computeMinutes))
                .multiplier(DEFAULT_MULTIPLIER)
                .reasonCode(truncate(reason, 80))
                .correlationId(UUID.randomUUID())
                .createdAt(createdAt)
                .build();
    }

    private void recalculateAvailable(UserQuotaBalanceCurrent balance) {
        balance.setAvailableMinutes(Math.max(
                0L,
                balance.getGrantedMinutes() - balance.getReservedMinutes() - balance.getConsumedMinutes()
        ));
    }

    private void bumpVersionAndUpdatedAt(UserQuotaBalanceCurrent balance, OffsetDateTime now) {
        balance.setUpdatedAt(now);
        balance.setVersion(balance.getVersion() + 1L);
    }

    private OffsetDateTime resolveJobReferencePoint(Job job) {
        var referenceTime = job.getQueuedAt() != null ? job.getQueuedAt() : job.getCreatedAt();
        return referenceTime != null ? referenceTime : OffsetDateTime.now(ZoneOffset.UTC);
    }

    private long normalizeComputeMinutes(long requestedMinutes, int multiplier) {
        return Math.max(0L, requestedMinutes) * Math.max(1, multiplier);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private QuotaIntervalBounds resolveBounds(OffsetDateTime at) {
        var utc = at.withOffsetSameInstant(ZoneOffset.UTC);
        var start = OffsetDateTime.of(utc.getYear(), utc.getMonthValue(), 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var end = start.plusMonths(1);
        return new QuotaIntervalBounds(start, end);
    }

    private record BalanceSettlement(
            long releasedReservedMinutes,
            long consumedMinutes,
            long refundedMinutes
    ) {
    }
}
