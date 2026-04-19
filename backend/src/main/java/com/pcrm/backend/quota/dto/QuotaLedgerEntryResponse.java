package com.pcrm.backend.quota.dto;

import com.pcrm.backend.quota.domain.QuotaLedgerEntry;
import com.pcrm.backend.quota.domain.QuotaLedgerEntryType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record QuotaLedgerEntryResponse(
        UUID id,
        UUID jobId,
        long leaseSequence,
        QuotaLedgerEntryType entryType,
        long minutes,
        String reason,
        OffsetDateTime createdAt
) {

    public static QuotaLedgerEntryResponse from(QuotaLedgerEntry entry) {
        return new QuotaLedgerEntryResponse(
                entry.getId(),
                entry.getJob() != null ? entry.getJob().getId() : null,
                entry.getLeaseSequence(),
                entry.getEntryType(),
                entry.getMinutes(),
                entry.getReason(),
                entry.getCreatedAt()
        );
    }
}
