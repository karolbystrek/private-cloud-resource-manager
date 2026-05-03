package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RunRepository extends JpaRepository<Run, UUID> {

    @EntityGraph(attributePaths = {"job", "job.user", "user"})
    List<Run> findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(RunStatus status);

    @EntityGraph(attributePaths = {"job", "job.user", "user"})
    Optional<Run> findByNomadJobId(String nomadJobId);

    @EntityGraph(attributePaths = {"job", "job.user", "user"})
    Optional<Run> findByNomadAllocationId(String nomadAllocationId);

    @EntityGraph(attributePaths = {"job", "job.user", "user"})
    Optional<Run> findFirstByJob_IdOrderByRunNumberDesc(UUID jobId);

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
