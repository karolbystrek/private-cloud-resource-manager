package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByUser_IdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<Job> findByUser_Id(UUID userId, Pageable pageable);

    Page<Job> findByUser_IdAndStatus(UUID userId, JobStatus status, Pageable pageable);

    Page<Job> findByUser_IdAndStatusIn(UUID userId, List<JobStatus> statuses, Pageable pageable);

    Optional<Job> findByIdAndUser_Id(UUID id, UUID userId);

    List<Job> findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus status);

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
