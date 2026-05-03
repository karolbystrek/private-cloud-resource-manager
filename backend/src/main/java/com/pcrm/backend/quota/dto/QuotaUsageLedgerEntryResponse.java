package com.pcrm.backend.quota.dto;

import com.pcrm.backend.quota.domain.QuotaUsageLedgerEntry;
import com.pcrm.backend.quota.domain.QuotaUsageLedgerEntryType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QuotaUsageLedgerEntryResponse(
        UUID id,
        UUID jobId,
        UUID runId,
        UUID quotaReservationId,
        QuotaUsageLedgerEntryType entryType,
        long computeMinutes,
        Integer multiplier,
        String reasonCode,
        UUID correlationId,
        OffsetDateTime createdAt
) {

    public static QuotaUsageLedgerEntryResponse from(QuotaUsageLedgerEntry entry) {
        return new QuotaUsageLedgerEntryResponse(
                entry.getId(),
                entry.getJob() == null ? null : entry.getJob().getId(),
                entry.getRun() == null ? null : entry.getRun().getId(),
                entry.getQuotaReservation() == null ? null : entry.getQuotaReservation().getId(),
                entry.getEntryType(),
                entry.getComputeMinutes(),
                entry.getMultiplier(),
                entry.getReasonCode(),
                entry.getCorrelationId(),
                entry.getCreatedAt()
        );
    }
}
