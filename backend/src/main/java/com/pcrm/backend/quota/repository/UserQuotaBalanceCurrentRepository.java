package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.UserQuotaBalanceCurrent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserQuotaBalanceCurrentRepository extends JpaRepository<UserQuotaBalanceCurrent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT balance
            FROM UserQuotaBalanceCurrent balance
            WHERE balance.profile.id = :userId
              AND balance.intervalStart = :intervalStart
            """)
    Optional<UserQuotaBalanceCurrent> findByProfileIdAndIntervalStartForUpdate(
            @Param("userId") UUID userId,
            @Param("intervalStart") OffsetDateTime intervalStart
    );

    Optional<UserQuotaBalanceCurrent> findByProfile_IdAndIntervalStart(UUID userId, OffsetDateTime intervalStart);
}
