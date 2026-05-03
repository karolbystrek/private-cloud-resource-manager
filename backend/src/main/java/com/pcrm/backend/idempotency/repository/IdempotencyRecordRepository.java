package com.pcrm.backend.idempotency.repository;

import com.pcrm.backend.idempotency.domain.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (
                id,
                tenant_id,
                actor_type,
                actor_id,
                workflow,
                idempotency_key,
                request_fingerprint,
                status,
                locked_until,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                NULL,
                :actorType,
                :actorId,
                :workflow,
                :idempotencyKey,
                :requestFingerprint,
                'IN_PROGRESS',
                :lockedUntil,
                :now,
                :now
            )
            ON CONFLICT (actor_type, actor_id, workflow, idempotency_key)
                WHERE tenant_id IS NULL
                DO NOTHING
            """, nativeQuery = true)
    int insertTenantlessInProgressIfAbsent(
            @Param("id") UUID id,
            @Param("actorType") String actorType,
            @Param("actorId") String actorId,
            @Param("workflow") String workflow,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestFingerprint") String requestFingerprint,
            @Param("lockedUntil") OffsetDateTime lockedUntil,
            @Param("now") OffsetDateTime now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT record
            FROM IdempotencyRecord record
            WHERE record.tenantId IS NULL
              AND record.actorType = :actorType
              AND record.actorId = :actorId
              AND record.workflow = :workflow
              AND record.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findTenantlessForUpdate(
            @Param("actorType") String actorType,
            @Param("actorId") String actorId,
            @Param("workflow") String workflow,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Query("""
            SELECT COUNT(record)
            FROM IdempotencyRecord record
            WHERE record.status = com.pcrm.backend.idempotency.domain.IdempotencyStatus.IN_PROGRESS
              AND record.updatedAt < :staleBefore
            """)
    long countStaleInProgress(@Param("staleBefore") OffsetDateTime staleBefore);

    @Modifying
    @Query(value = """
            DELETE FROM idempotency_records
            WHERE updated_at < :staleBefore
            """, nativeQuery = true)
    int deleteStaleRecords(@Param("staleBefore") OffsetDateTime staleBefore);
}
