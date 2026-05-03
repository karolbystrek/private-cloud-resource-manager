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
import com.pcrm.backend.quota.service.QuotaAccountingService;
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
    private final QuotaAccountingService quotaAccountingService;
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
        var savedJob = createQueuedJob(user, request, idempotencyKey, submissionFingerprint, now);
        var savedRun = createQueuedRun(savedJob, user, now);
        savedJob.setCurrentRun(savedRun);
        jobRepository.save(savedJob);

        var correlationId = UUID.randomUUID();
        eventPublisher.jobSubmitted(savedJob, userId.toString(), idempotencyKey, correlationId);
        eventPublisher.runEvent("RunCreated", savedRun, correlationId);

        var initialReservedMinutes = quotaAccountingService.reserveInitialLease(
                userId,
                savedRun,
                "Initial 15-minute lease reservation"
        );

        savedRun.setCurrentLeaseReservedMinutes(initialReservedMinutes);
        savedRun.setActiveLeaseExpiresAt(now.plusMinutes(initialReservedMinutes));
        runRepository.save(savedRun);

        savedJob.setCurrentLeaseReservedMinutes(initialReservedMinutes);
        savedJob.setActiveLeaseExpiresAt(now.plusMinutes(initialReservedMinutes));
        jobRepository.save(savedJob);

        eventPublisher.runEvent("RunLeaseReserved", savedRun, correlationId);
        eventPublisher.runEvent("RunQueued", savedRun, correlationId);

        log.debug("Prepared queued job submission for user {}: jobId#{}", user.getUsername(), savedJob.getId());
        return PreparedJobSubmission.created(savedJob.getId(), savedRun.getId(), userId, initialReservedMinutes);
    }

    public void compensateFailedDispatch(PreparedJobSubmission preparedJobSubmission) {
        var job = jobRepository.findById(preparedJobSubmission.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", preparedJobSubmission.jobId()));

        var run = runRepository.findById(preparedJobSubmission.runId()).orElse(job.getCurrentRun());
        if (run != null) {
            quotaAccountingService.refundLeaseReservation(
                    run,
                    preparedJobSubmission.initialReservedMinutes(),
                    "Nomad dispatch failed, initial lease refunded"
            );
            run.setCurrentLeaseReservedMinutes(0L);
            run.setLeaseSettled(true);
            run.setStatus(RunStatus.INFRA_FAILED);
            run.setTerminalReason("NOMAD_DISPATCH_FAILED");
            run.setProcessFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
            runRepository.save(run);
            eventPublisher.runEvent("RunInfraFailed", run, UUID.randomUUID());
        } else {
            quotaAccountingService.refundLeaseReservation(
                    job,
                    preparedJobSubmission.initialReservedMinutes(),
                    "Nomad dispatch failed, initial lease refunded"
            );
        }

        job.setCurrentLeaseReservedMinutes(0L);
        job.setLeaseSettled(true);
        job.setStatus(RunStatus.INFRA_FAILED);
        job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        jobRepository.save(job);

        log.debug("Compensated failed dispatch for jobId#{}", preparedJobSubmission.jobId());
    }

    private Job createQueuedJob(
            User user,
            JobSubmissionRequest request,
            String idempotencyKey,
            String submissionFingerprint,
            OffsetDateTime now
    ) {
        var leaseMinutes = quotaAccountingService.getLeaseMinutes();
        var job = Job.builder()
                .id(UUID.randomUUID())
                .user(user)
                .nodeId(null)
                .status(RunStatus.QUEUED)
                .dockerImage(request.dockerImage())
                .executionCommand(request.executionCommand())
                .idempotencyKey(idempotencyKey)
                .submissionFingerprint(submissionFingerprint)
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

    private Run createQueuedRun(Job job, User user, OffsetDateTime now) {
        var leaseMinutes = quotaAccountingService.getLeaseMinutes();
        var run = Run.builder()
                .id(UUID.randomUUID())
                .job(job)
                .user(user)
                .runNumber(1)
                .status(RunStatus.QUEUED)
                .queuedAt(now)
                .activeLeaseExpiresAt(now.plusMinutes(leaseMinutes))
                .currentLeaseReservedMinutes(0L)
                .leaseSequence(1L)
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
