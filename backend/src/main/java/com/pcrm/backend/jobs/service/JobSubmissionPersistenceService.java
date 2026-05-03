package com.pcrm.backend.jobs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.repository.RunRepository;
import com.pcrm.backend.user.User;
import com.pcrm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSubmissionPersistenceService {

    private final JobRepository jobRepository;
    private final RunRepository runRepository;
    private final UserRepository userRepository;
    private final JobRunEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public PreparedJobSubmission prepareSubmission(
            UUID userId,
            JobSubmissionRequest request,
            String idempotencyKey,
            String submissionFingerprint
    ) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var savedJob = createSubmittedJob(user, request, idempotencyKey, submissionFingerprint, now);
        var savedRun = createSubmittedRun(savedJob, user, now);
        savedJob.setCurrentRun(savedRun);
        jobRepository.save(savedJob);

        var correlationId = UUID.randomUUID();
        eventPublisher.jobSubmitted(savedJob, userId.toString(), idempotencyKey, correlationId);
        eventPublisher.runSubmitted(savedRun, correlationId);

        log.debug("Prepared submitted job intent for user {}: jobId#{}", user.getUsername(), savedJob.getId());
        return PreparedJobSubmission.created(savedJob.getId(), savedRun.getId(), userId);
    }

    private Job createSubmittedJob(
            User user,
            JobSubmissionRequest request,
            String idempotencyKey,
            String submissionFingerprint,
            OffsetDateTime now
    ) {
        var job = Job.builder()
                .id(UUID.randomUUID())
                .user(user)
                .nodeId(null)
                .status(RunStatus.SUBMITTED)
                .dockerImage(request.dockerImage())
                .executionCommand(request.executionCommand())
                .idempotencyKey(idempotencyKey)
                .submissionFingerprint(submissionFingerprint)
                .reqCpuCores(request.reqCpuCores())
                .reqRamGb(request.reqRamGb())
                .envVarsJson(serializeEnvVars(request))
                .queuedAt(null)
                .activeLeaseExpiresAt(null)
                .currentLeaseReservedMinutes(0L)
                .leaseSequence(0L)
                .leaseSettled(false)
                .totalConsumedMinutes(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return jobRepository.save(job);
    }

    private Run createSubmittedRun(Job job, User user, OffsetDateTime now) {
        var run = Run.builder()
                .id(UUID.randomUUID())
                .job(job)
                .user(user)
                .runNumber(1)
                .status(RunStatus.SUBMITTED)
                .queuedAt(null)
                .activeLeaseExpiresAt(null)
                .currentLeaseReservedMinutes(0L)
                .leaseSequence(0L)
                .leaseSettled(false)
                .totalConsumedMinutes(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return runRepository.save(run);
    }

    private String serializeEnvVars(JobSubmissionRequest request) {
        try {
            return objectMapper.writeValueAsString(request.envVars());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize environment variables", ex);
        }
    }
}
