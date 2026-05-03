package com.pcrm.backend.quota.service;

import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.events.service.AggregateIds;
import com.pcrm.backend.events.service.DomainEventAppendRequest;
import com.pcrm.backend.events.service.DomainEventAppender;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.quota.domain.QuotaGrant;
import com.pcrm.backend.quota.domain.QuotaGrantStatus;
import com.pcrm.backend.quota.domain.QuotaGrantType;
import com.pcrm.backend.quota.domain.QuotaReservation;
import com.pcrm.backend.quota.domain.QuotaReservationStatus;
import com.pcrm.backend.quota.domain.QuotaUsageLedgerEntry;
import com.pcrm.backend.quota.domain.QuotaUsageLedgerEntryType;
import com.pcrm.backend.quota.domain.UserQuotaBalanceCurrent;
import com.pcrm.backend.quota.dto.QuotaSummaryResponse;
import com.pcrm.backend.quota.dto.QuotaUsageLedgerEntryResponse;
import com.pcrm.backend.quota.dto.admin.AdminQuotaGrantRequest;
import com.pcrm.backend.quota.dto.admin.AdminQuotaGrantResponse;
import com.pcrm.backend.quota.repository.QuotaGrantRepository;
import com.pcrm.backend.quota.repository.QuotaReservationRepository;
import com.pcrm.backend.quota.repository.QuotaUsageLedgerRepository;
import com.pcrm.backend.quota.repository.UserQuotaBalanceCurrentRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaAccountingService {

    private static final String COMPUTE_RESOURCE_CLASS = "compute";
    private static final int DEFAULT_MULTIPLIER = 1;

    private final UserRepository userRepository;
    private final QuotaGrantRepository quotaGrantRepository;
    private final QuotaReservationRepository quotaReservationRepository;
    private final QuotaUsageLedgerRepository quotaUsageLedgerRepository;
    private final UserQuotaBalanceCurrentRepository userQuotaBalanceCurrentRepository;
    private final QuotaPolicyResolverService quotaPolicyResolverService;
    private final DomainEventAppender domainEventAppender;

    @Value("${app.quota.lease-minutes:15}")
    private long leaseMinutes;

    @Transactional
    public long reserveInitialLease(UUID userId, Run run, String reason) {
        var existingReservation = quotaReservationRepository
                .findByRunIdAndStatusForUpdate(run.getId(), QuotaReservationStatus.ACTIVE);
        if (existingReservation.isPresent()) {
            run.setQuotaReservationId(existingReservation.get().getId());
            return existingReservation.get().getReservedComputeMinutes();
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, now);
        var bounds = resolveBounds(now);
        var balance = getOrCreateBalanceForUpdate(user, bounds, effectivePolicy, now);
        var computeMinutes = normalizeComputeMinutes(leaseMinutes, DEFAULT_MULTIPLIER);

        if (!effectivePolicy.unlimited() && balance.getAvailableMinutes() < computeMinutes) {
            throw new InsufficientQuotaException(balance.getAvailableMinutes(), computeMinutes);
        }

        var reservation = quotaReservationRepository.save(QuotaReservation.builder()
                .user(user)
                .job(run.getJob())
                .run(run)
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
        run.setQuotaReservationId(reservation.getId());

        reserveBalanceMinutes(balance, computeMinutes, now);
        appendQuotaEvent("QuotaReserved", user, run, reservation, "RESERVATION_CREATED", computeMinutes, reason, now);
        return computeMinutes;
    }

    @Transactional
    public void refundLeaseReservation(Run run, long minutes, String reason) {
        settleLeaseMinutes(run, minutes, 0L, reason);
    }

    @Transactional
    public void settleLeaseMinutes(Run run, long reservedMinutes, long consumedMinutes, String reason) {
        var user = userRepository.findById(run.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", run.getUser().getId()));
        var referencePoint = resolveRunReferencePoint(run);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, referencePoint);
        var bounds = resolveBounds(referencePoint);
        var balance = getOrCreateBalanceForUpdate(user, bounds, effectivePolicy, now);
        var reservation = findReservationForSettlement(run);

        if (reservation == null) {
            var settlement = settleBalanceMinutes(balance, Math.max(0L, reservedMinutes), Math.max(0L, consumedMinutes), now);
            appendSettlementFacts(user, run, null, settlement, reason, now);
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
        appendSettlementFacts(user, run, reservation, settlement, reason, now);
    }

    @Transactional(readOnly = true)
    public QuotaFairnessSnapshot loadFairnessSnapshot(UUID userId, OffsetDateTime at) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, at);
        var bounds = resolveBounds(at);
        var existingBalance = userQuotaBalanceCurrentRepository.findByUser_IdAndIntervalStart(userId, bounds.start());

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
    public QuotaSummaryResponse getQuotaSummary(UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, now);
        var bounds = resolveBounds(now);
        var balance = getOrCreateBalanceForUpdate(user, bounds, effectivePolicy, now);

        long remaining = effectivePolicy.unlimited()
                ? Long.MAX_VALUE
                : balance.getAvailableMinutes();

        return new QuotaSummaryResponse(
                user.getRole(),
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
    public List<QuotaUsageLedgerEntryResponse> getQuotaUsageLedger(UUID userId, YearMonth window) {
        var start = OffsetDateTime.of(window.getYear(), window.getMonthValue(), 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var end = start.plusMonths(1);

        return quotaUsageLedgerRepository
                .findByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(userId, start, end)
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
        var user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
        var actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", actorId));
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var effectivePolicy = quotaPolicyResolverService.resolveEffectivePolicy(user, bounds.start());
        var balance = getOrCreateBalanceForUpdate(user, bounds, effectivePolicy, now);
        var minutes = Math.max(0L, request.minutes());

        var grant = quotaGrantRepository.save(QuotaGrant.builder()
                .user(user)
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

        appendGrantEvent("QuotaGrantAdded", grant, balance, actor, idempotencyKey, now);
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
            User user,
            QuotaIntervalBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        return userQuotaBalanceCurrentRepository.findByUserIdAndIntervalStartForUpdate(user.getId(), bounds.start())
                .orElseGet(() -> createAndLockBalance(user, bounds, effectivePolicy, now));
    }

    private UserQuotaBalanceCurrent createAndLockBalance(
            User user,
            QuotaIntervalBounds bounds,
            EffectiveQuotaPolicy effectivePolicy,
            OffsetDateTime now
    ) {
        var monthlyMinutes = Math.max(0L, effectivePolicy.monthlyMinutes());
        var newBalance = UserQuotaBalanceCurrent.builder()
                .user(user)
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
                        .user(user)
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
                appendGrantEvent("QuotaGrantAdded", grant, newBalance, null, null, now);
            }
        } catch (DataIntegrityViolationException ignored) {
        }

        return userQuotaBalanceCurrentRepository.findByUserIdAndIntervalStartForUpdate(user.getId(), bounds.start())
                .orElseThrow(() -> new ResourceNotFoundException("UserQuotaBalanceCurrent", "userId", user.getId().toString()));
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

    private QuotaReservation findReservationForSettlement(Run run) {
        if (run.getQuotaReservationId() != null) {
            return quotaReservationRepository.findByIdForUpdate(run.getQuotaReservationId()).orElse(null);
        }
        return quotaReservationRepository
                .findByRunIdAndStatusForUpdate(run.getId(), QuotaReservationStatus.ACTIVE)
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
            User user,
            Run run,
            QuotaReservation reservation,
            BalanceSettlement settlement,
            String reason,
            OffsetDateTime now
    ) {
        if (settlement.consumedMinutes() > 0) {
            quotaUsageLedgerRepository.save(buildUsageLedgerEntry(
                    user,
                    run,
                    reservation,
                    QuotaUsageLedgerEntryType.USAGE_DEBITED,
                    settlement.consumedMinutes(),
                    reason,
                    now
            ));
            appendQuotaEvent("QuotaConsumed", user, run, reservation, QuotaUsageLedgerEntryType.USAGE_DEBITED.name(), settlement.consumedMinutes(), reason, now);
        }
        if (settlement.refundedMinutes() > 0) {
            quotaUsageLedgerRepository.save(buildUsageLedgerEntry(
                    user,
                    run,
                    reservation,
                    QuotaUsageLedgerEntryType.USAGE_RELEASED,
                    settlement.refundedMinutes(),
                    reason,
                    now
            ));
            appendQuotaEvent("QuotaReleased", user, run, reservation, QuotaUsageLedgerEntryType.USAGE_RELEASED.name(), settlement.refundedMinutes(), reason, now);
        }
    }

    private QuotaUsageLedgerEntry buildUsageLedgerEntry(
            User user,
            Run run,
            QuotaReservation reservation,
            QuotaUsageLedgerEntryType type,
            long computeMinutes,
            String reason,
            OffsetDateTime createdAt
    ) {
        return QuotaUsageLedgerEntry.builder()
                .user(user)
                .job(run.getJob())
                .run(run)
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

    private void appendQuotaEvent(
            String eventType,
            User user,
            Run run,
            QuotaReservation reservation,
            String factType,
            long minutes,
            String reason,
            OffsetDateTime occurredAt
    ) {
        var reference = occurredAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : occurredAt;
        var aggregateId = AggregateIds.quotaBalance(user.getId(), resolveBounds(reference).start(), COMPUTE_RESOURCE_CLASS);
        domainEventAppender.append(new DomainEventAppendRequest(
                eventType,
                AggregateIds.QUOTA_BALANCE,
                aggregateId,
                Map.of(
                        "userId", user.getId(),
                        "jobId", run.getJob().getId(),
                        "runId", run.getId(),
                        "quotaReservationId", reservation == null ? "" : reservation.getId(),
                        "factType", factType,
                        "minutes", Math.max(0L, minutes),
                        "reason", reason == null ? "" : reason
                ),
                Map.of(),
                "backend",
                "SYSTEM",
                "quota-accounting",
                user.getId(),
                run.getJob().getId(),
                null,
                UUID.randomUUID(),
                null,
                occurredAt,
                1,
                List.of(eventType)
        ));
    }

    private void appendGrantEvent(
            String eventType,
            QuotaGrant grant,
            UserQuotaBalanceCurrent balance,
            User actor,
            String idempotencyKey,
            OffsetDateTime occurredAt
    ) {
        var aggregateId = AggregateIds.quotaBalance(grant.getUser().getId(), grant.getIntervalStart(), COMPUTE_RESOURCE_CLASS);
        domainEventAppender.append(new DomainEventAppendRequest(
                eventType,
                AggregateIds.QUOTA_BALANCE,
                aggregateId,
                Map.of(
                        "userId", grant.getUser().getId(),
                        "quotaGrantId", grant.getId(),
                        "grantType", grant.getGrantType().name(),
                        "minutes", grant.getMinutes(),
                        "remainingMinutes", grant.getRemainingMinutes(),
                        "intervalStart", grant.getIntervalStart(),
                        "intervalEnd", grant.getIntervalEnd(),
                        "grantedMinutes", balance.getGrantedMinutes(),
                        "availableMinutes", balance.getAvailableMinutes(),
                        "reason", grant.getReason() == null ? "" : grant.getReason()
                ),
                Map.of(),
                "backend",
                actor == null ? "SYSTEM" : "USER",
                actor == null ? "quota-accounting" : actor.getId().toString(),
                grant.getUser().getId(),
                null,
                null,
                UUID.randomUUID(),
                idempotencyKey,
                occurredAt,
                1,
                List.of(eventType)
        ));
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

    private OffsetDateTime resolveRunReferencePoint(Run run) {
        var referenceTime = run.getQueuedAt() != null ? run.getQueuedAt() : run.getCreatedAt();
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
