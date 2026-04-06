package com.pcrm.backend.jobs.repository;

import com.pcrm.backend.jobs.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByUser_IdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
