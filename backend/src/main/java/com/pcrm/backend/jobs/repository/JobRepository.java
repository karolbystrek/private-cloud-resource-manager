package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Page<Job> findByProfile_Id(UUID userId, Pageable pageable);

    Page<Job> findByProfile_IdAndStatusIn(UUID userId, List<JobStatus> statuses, Pageable pageable);

    Optional<Job> findByIdAndProfile_Id(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"profile"})
    List<Job> findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"profile"})
    @Query("""
            SELECT job
            FROM Job job
            WHERE job.id = :jobId
            """)
    Optional<Job> findByIdForUpdate(@Param("jobId") UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"profile"})
    @Query("""
            SELECT job
            FROM Job job
            WHERE job.status IN :statuses
              AND job.leaseSettled = false
              AND job.activeLeaseExpiresAt IS NOT NULL
              AND job.activeLeaseExpiresAt <= :threshold
            ORDER BY job.activeLeaseExpiresAt ASC
            """)
    List<Job> findLeaseEnforcementCandidatesForUpdate(
            @Param("statuses") Collection<JobStatus> statuses,
            @Param("threshold") OffsetDateTime threshold,
            Pageable pageable
    );

    @Modifying
    @Query("""
            UPDATE Job job
            SET job.status = :nextStatus
            WHERE job.id = :jobId
              AND job.status = :expectedStatus
            """)
    int transitionStatus(
            @Param("jobId") UUID jobId,
            @Param("expectedStatus") JobStatus expectedStatus,
            @Param("nextStatus") JobStatus nextStatus
    );
}
