package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.UserQuotaOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserQuotaOverrideRepository extends JpaRepository<UserQuotaOverride, UUID> {

    @Query(value = """
            SELECT *
            FROM user_quota_override
            WHERE user_id = :userId
              AND active_from <= :at
              AND (expires_at IS NULL OR expires_at > :at)
            ORDER BY active_from DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<UserQuotaOverride> findActiveOverride(@Param("userId") UUID userId, @Param("at") OffsetDateTime at);
}
