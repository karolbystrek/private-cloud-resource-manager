package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RunRepository extends JpaRepository<Run, UUID> {

    @EntityGraph(attributePaths = {"job", "job.profile", "profile"})
    List<Run> findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(RunStatus status);

    @EntityGraph(attributePaths = {"job", "job.profile", "profile"})
    Optional<Run> findByNomadJobId(String nomadJobId);

    @EntityGraph(attributePaths = {"job", "job.profile", "profile"})
    Optional<Run> findByNomadAllocationId(String nomadAllocationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"job", "job.profile", "profile"})
    @Query("""
            SELECT run
            FROM Run run
            WHERE run.id = :runId
            """)
    Optional<Run> findByIdForUpdate(@Param("runId") UUID runId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"job", "job.profile", "profile"})
    @Query("""
            SELECT run
            FROM Run run
            WHERE run.status IN :statuses
              AND run.leaseSettled = false
              AND run.activeLeaseExpiresAt IS NOT NULL
              AND run.activeLeaseExpiresAt <= :threshold
            ORDER BY run.activeLeaseExpiresAt ASC
            """)
    List<Run> findLeaseEnforcementCandidatesForUpdate(
            @Param("statuses") Collection<RunStatus> statuses,
            @Param("threshold") OffsetDateTime threshold,
            Pageable pageable
    );

    @Modifying
    @Query("""
            UPDATE Run run
            SET run.status = :nextStatus
            WHERE run.id = :runId
              AND run.status = :expectedStatus
            """)
    int transitionStatus(
            @Param("runId") UUID runId,
            @Param("expectedStatus") RunStatus expectedStatus,
            @Param("nextStatus") RunStatus nextStatus
    );
}
