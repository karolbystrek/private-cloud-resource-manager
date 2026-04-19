package com.pcrm.backend.jobs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.exception.IdempotencyKeyConflictException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.user.User;
import com.pcrm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSubmissionPersistenceService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final QuotaAccountingService quotaAccountingService;
    private final ObjectMapper objectMapper;

    public PreparedJobSubmission prepareSubmission(
            UUID userId,
            JobSubmissionRequest request,
            JobSubmissionIdempotency idempotency
    ) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        var replayedSubmission = findReplayedSubmission(userId, idempotency, user.getUsername());
        if (replayedSubmission.isPresent()) {
            return replayedSubmission.get();
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var savedJob = createQueuedJob(user, request, idempotency, now);
        var initialReservedMinutes = quotaAccountingService.reserveInitialLease(
                userId,
                savedJob,
                "Initial 15-minute lease reservation"
        );

        savedJob.setCurrentLeaseReservedMinutes(initialReservedMinutes);
        savedJob.setActiveLeaseExpiresAt(now.plusMinutes(initialReservedMinutes));
        jobRepository.save(savedJob);

        log.debug("Prepared queued job submission for user {}: jobId#{}", user.getUsername(), savedJob.getId());
        return PreparedJobSubmission.created(savedJob.getId(), userId, initialReservedMinutes);
    }

    public void compensateFailedDispatch(PreparedJobSubmission preparedJobSubmission) {
        var job = jobRepository.findById(preparedJobSubmission.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", preparedJobSubmission.jobId()));

        quotaAccountingService.refundLeaseReservation(
                job,
                preparedJobSubmission.initialReservedMinutes(),
                "Nomad dispatch failed, initial lease refunded"
        );

        job.setCurrentLeaseReservedMinutes(0L);
        job.setLeaseSettled(true);
        job.setStatus(JobStatus.FAILED);
        job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        jobRepository.save(job);

        log.debug("Compensated failed dispatch for jobId#{}", preparedJobSubmission.jobId());
    }

    private Optional<PreparedJobSubmission> findReplayedSubmission(
            UUID userId,
            JobSubmissionIdempotency idempotency,
            String username
    ) {
        var existingJob = jobRepository.findByUser_IdAndIdempotencyKey(userId, idempotency.key());
        if (existingJob.isEmpty()) {
            return Optional.empty();
        }

        var persistedJob = existingJob.get();
        if (!Objects.equals(persistedJob.getSubmissionFingerprint(), idempotency.fingerprint())) {
            throw new IdempotencyKeyConflictException();
        }

        log.debug("Replayed idempotent job submission for user {}: jobId#{}", username, persistedJob.getId());
        return Optional.of(PreparedJobSubmission.replayed(persistedJob.getId(), userId));
    }

    private Job createQueuedJob(
            User user,
            JobSubmissionRequest request,
            JobSubmissionIdempotency idempotency,
            OffsetDateTime now
    ) {
        var leaseMinutes = quotaAccountingService.getLeaseMinutes();
        var job = Job.builder()
                .id(UUID.randomUUID())
                .user(user)
                .nodeId(null)
                .status(JobStatus.QUEUED)
                .dockerImage(request.dockerImage())
                .executionCommand(request.executionCommand())
                .idempotencyKey(idempotency.key())
                .submissionFingerprint(idempotency.fingerprint())
                .reqCpuCores(request.reqCpuCores())
                .reqRamGb(request.reqRamGb())
                .envVarsJson(serializeEnvVars(request))
                .queuedAt(now)
                .activeLeaseExpiresAt(now.plusMinutes(leaseMinutes))
                .currentLeaseReservedMinutes(0L)
                .leaseSequence(1L)
                .leaseSettled(false)
                .totalConsumedMinutes(0L)
                .build();

        return jobRepository.save(job);
    }

    private String serializeEnvVars(JobSubmissionRequest request) {
        try {
            return objectMapper.writeValueAsString(request.envVars());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize environment variables", ex);
        }
    }
}
