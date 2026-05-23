package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.JobLogChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobLogChunkRepository extends JpaRepository<JobLogChunk, UUID> {

    List<JobLogChunk> findByJob_IdAndStreamOrderBySequenceAsc(UUID jobId, String stream);

    List<JobLogChunk> findByJob_IdAndStreamAndSequenceGreaterThanOrderBySequenceAsc(
            UUID jobId,
            String stream,
            Long sequence
    );

    @Query("""
            SELECT chunk
            FROM JobLogChunk chunk
            WHERE chunk.logStream.id = :streamId
            ORDER BY chunk.sequence DESC
            """)
    List<JobLogChunk> findLatestForStream(
            @Param("streamId") UUID streamId,
            Pageable pageable
    );
}
