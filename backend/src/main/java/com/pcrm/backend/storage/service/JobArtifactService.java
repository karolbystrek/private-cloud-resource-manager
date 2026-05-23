package com.pcrm.backend.storage.service;

import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.service.JobStateMachine;
import com.pcrm.backend.storage.domain.JobArtifact;
import com.pcrm.backend.storage.domain.JobArtifactStatus;
import com.pcrm.backend.storage.repository.JobArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class JobArtifactService {

    private static final String ARTIFACT_MISSING = "ARTIFACT_MISSING";
    private static final int MAX_FAILURE_REASON_LENGTH = 4000;

    private final JobArtifactRepository artifactRepository;
    private final JobRepository jobRepository;
    private final StorageService storageService;
    private final JobStateMachine jobStateMachine;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.storage.artifacts.finalizer-batch-size:50}")
    private int finalizerBatchSize;

    @Value("${app.storage.artifacts.missing-timeout-ms:120000}")
    private long missingTimeoutMs;

    @Scheduled(fixedDelayString = "${app.storage.artifacts.finalizer-interval-ms:10000}")
    public void finalizeCompletedArtifacts() {
        if (finalizerBatchSize <= 0) {
            return;
        }

        jobRepository.findTop100ByStatusOrderByProcessFinishedAtAscCreatedAtAsc(JobStatus.FINALIZING)
                .stream()
                .limit(finalizerBatchSize)
                .map(Job::getId)
                .forEach(this::finalizeJobArtifact);
    }

    @Transactional
    public JobArtifact ensurePendingArtifact(Job job) {
        return artifactRepository.findByJobIdForUpdate(job.getId())
                .orElseGet(() -> artifactRepository.save(JobArtifact.builder()
                        .job(job)
                        .profile(job.getProfile())
                        .status(JobArtifactStatus.PENDING)
                        .objectKey(storageService.buildArtifactObjectKey(job.getProfile().getId(), job.getId()))
                        .build()));
    }

    @Transactional
    public JobArtifact ensurePendingArtifact(UUID jobId) {
        var job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId.toString()));
        return ensurePendingArtifact(job);
    }

    @Transactional
    public JobArtifact markUploading(Job job) {
        var artifact = ensurePendingArtifact(job);
        if (artifact.getStatus() == JobArtifactStatus.PENDING) {
            artifact.setStatus(JobArtifactStatus.UPLOADING);
            artifact.setFailureReason(null);
            return artifactRepository.save(artifact);
        }
        return artifact;
    }

    @Transactional(readOnly = true)
    public JobArtifact getAvailableArtifactForDownload(UUID jobId, UUID profileId) {
        var artifact = artifactRepository.findByJob_Id(jobId)
                .filter(found -> found.getProfile().getId().equals(profileId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact is not available yet."));

        if (artifact.getStatus() != JobArtifactStatus.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact is not available yet.");
        }
        return artifact;
    }

    public JobArtifact getVerifiedDownloadableArtifact(UUID jobId, UUID profileId) {
        var artifact = getAvailableArtifactForDownload(jobId, profileId);
        var metadata = storageService.getObjectMetadata(artifact.getObjectKey());
        if (metadata.exists()) {
            return artifact;
        }

        transactionTemplate.executeWithoutResult(_ -> markArtifactMissingIfStillAvailable(jobId, ARTIFACT_MISSING));
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact is not available yet.");
    }

    private void finalizeJobArtifact(UUID jobId) {
        ArtifactCheck check = transactionTemplate.execute(_ -> prepareArtifactCheck(jobId));
        if (check == null) {
            return;
        }

        StorageService.StoredObjectMetadata metadata;
        try {
            metadata = storageService.getObjectMetadata(check.objectKey());
        } catch (Exception ex) {
            log.warn("Artifact check failed for job {}: {}", jobId, ex.getMessage());
            return;
        }

        transactionTemplate.executeWithoutResult(_ -> applyArtifactCheck(check, metadata));
    }

    private ArtifactCheck prepareArtifactCheck(UUID jobId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.FINALIZING) {
            return null;
        }

        var artifact = markUploading(job);
        return new ArtifactCheck(
                job.getId(),
                artifact.getObjectKey(),
                job.getProcessFinishedAt() == null ? job.getCreatedAt() : job.getProcessFinishedAt(),
                UUID.randomUUID()
        );
    }

    private void applyArtifactCheck(ArtifactCheck check, StorageService.StoredObjectMetadata metadata) {
        var job = jobRepository.findByIdForUpdate(check.jobId()).orElse(null);
        if (job == null || job.getStatus() != JobStatus.FINALIZING) {
            return;
        }

        var artifact = artifactRepository.findByJobIdForUpdate(job.getId())
                .orElseThrow(() -> new ResourceNotFoundException("JobArtifact", "jobId", job.getId().toString()));
        var now = OffsetDateTime.now(ZoneOffset.UTC);

        if (metadata.exists()) {
            artifact.setStatus(JobArtifactStatus.AVAILABLE);
            artifact.setSizeBytes(metadata.sizeBytes());
            artifact.setChecksumSha256(normalizeChecksum(metadata.checksum()));
            artifact.setFinalizedAt(now);
            artifact.setFailureReason(null);
            artifactRepository.save(artifact);

            jobStateMachine.markArtifactAvailable(job, now);
            log.info("Finalized artifact for job {}", job.getId());
            return;
        }

        if (!isMissingTimedOut(check.processFinishedAt(), now)) {
            return;
        }

        markArtifactFailed(job, artifact, now, ARTIFACT_MISSING, check.correlationId());
    }

    private void markArtifactMissingIfStillAvailable(UUID jobId, String reason) {
        var artifact = artifactRepository.findByJobIdForUpdate(jobId).orElse(null);
        if (artifact == null || artifact.getStatus() != JobArtifactStatus.AVAILABLE) {
            return;
        }

        artifact.setStatus(JobArtifactStatus.MISSING);
        artifact.setFailureReason(reason);
        artifact.setFinalizedAt(OffsetDateTime.now(ZoneOffset.UTC));
        artifactRepository.save(artifact);
    }

    private void markArtifactFailed(
            Job job,
            JobArtifact artifact,
            OffsetDateTime now,
            String reason,
            UUID correlationId
    ) {
        artifact.setStatus(JobArtifactStatus.MISSING);
        artifact.setFinalizedAt(now);
        artifact.setFailureReason(truncate(reason));
        artifactRepository.save(artifact);

        jobStateMachine.markArtifactFailed(job, now, reason);
        log.warn("Marked job {} failed because artifact was missing", job.getId());
    }

    private boolean isMissingTimedOut(OffsetDateTime processFinishedAt, OffsetDateTime now) {
        var finishedAt = processFinishedAt == null ? now : processFinishedAt;
        return !finishedAt.plus(Duration.ofMillis(Math.max(0L, missingTimeoutMs))).isAfter(now);
    }

    private String normalizeChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return null;
        }
        var normalized = checksum.replace("\"", "");
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private record ArtifactCheck(
            UUID jobId,
            String objectKey,
            OffsetDateTime processFinishedAt,
            UUID correlationId
    ) {
    }
}
