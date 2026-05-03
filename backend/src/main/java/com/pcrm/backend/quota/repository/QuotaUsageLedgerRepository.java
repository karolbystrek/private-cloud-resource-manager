package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.QuotaUsageLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuotaUsageLedgerRepository extends JpaRepository<QuotaUsageLedgerEntry, UUID> {

    List<QuotaUsageLedgerEntry> findByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID userId,
            OffsetDateTime start,
            OffsetDateTime end
    );
}
