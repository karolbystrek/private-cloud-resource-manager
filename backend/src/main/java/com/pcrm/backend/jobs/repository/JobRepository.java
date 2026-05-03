package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.RunStatus;
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

    Page<Job> findByUser_Id(UUID userId, Pageable pageable);

    Page<Job> findByUser_IdAndStatusIn(UUID userId, List<RunStatus> statuses, Pageable pageable);

    Optional<Job> findByIdAndUser_Id(UUID id, UUID userId);

    List<Job> findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(RunStatus status);

    @Modifying
    @Query("""
            UPDATE Job job
            SET job.status = :nextStatus
            WHERE job.id = :jobId
              AND job.status = :expectedStatus
            """)
    int transitionStatus(
            @Param("jobId") UUID jobId,
            @Param("expectedStatus") RunStatus expectedStatus,
            @Param("nextStatus") RunStatus nextStatus
    );
}
