package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.QuotaLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuotaLedgerRepository extends JpaRepository<QuotaLedgerEntry, UUID> {

    List<QuotaLedgerEntry> findByUser_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to
    );
}
