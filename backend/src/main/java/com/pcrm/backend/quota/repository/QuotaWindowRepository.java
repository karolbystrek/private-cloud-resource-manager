package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.QuotaWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotaWindowRepository extends JpaRepository<QuotaWindow, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT window
            FROM QuotaWindow window
            WHERE window.user.id = :userId
              AND window.windowStart = :windowStart
            """)
    Optional<QuotaWindow> findByUserIdAndWindowStartForUpdate(
            @Param("userId") UUID userId,
            @Param("windowStart") OffsetDateTime windowStart
    );

    Optional<QuotaWindow> findByUser_IdAndWindowStart(UUID userId, OffsetDateTime windowStart);
}
