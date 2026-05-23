package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.JobLogStream;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobLogStreamRepository extends JpaRepository<JobLogStream, UUID> {

    @EntityGraph(attributePaths = {"job", "profile"})
    Optional<JobLogStream> findByJob_IdAndStream(UUID jobId, String stream);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"job", "profile"})
    @Query("""
            SELECT stream
            FROM JobLogStream stream
            WHERE stream.job.id = :jobId
              AND stream.stream = :stream
            """)
    Optional<JobLogStream> findByJobIdAndStreamForUpdate(
            @Param("jobId") UUID jobId,
            @Param("stream") String stream
    );
}
