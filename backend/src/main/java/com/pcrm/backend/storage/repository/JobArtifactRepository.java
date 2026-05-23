package com.pcrm.backend.storage.repository;

import com.pcrm.backend.storage.domain.JobArtifact;
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
public interface JobArtifactRepository extends JpaRepository<JobArtifact, UUID> {

    @EntityGraph(attributePaths = {"job", "profile"})
    Optional<JobArtifact> findByJob_Id(UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"job", "profile"})
    @Query("""
            SELECT artifact
            FROM JobArtifact artifact
            WHERE artifact.job.id = :jobId
            """)
    Optional<JobArtifact> findByJobIdForUpdate(@Param("jobId") UUID jobId);
}
