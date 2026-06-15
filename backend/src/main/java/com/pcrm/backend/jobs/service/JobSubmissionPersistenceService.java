package com.pcrm.backend.jobs.service;

import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.user.Profile;
import com.pcrm.backend.user.repository.ProfileRepository;
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
    private final ProfileRepository profileRepository;
    private final JobOutboxPublisher outboxPublisher;

    public PreparedJobSubmission prepareSubmission(
            UUID profileId,
            JobSubmissionRequest request,
            String idempotencyKey
    ) {
        var profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", profileId));

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var savedJob = createSubmittedJob(profile, request, now);

        var correlationId = UUID.randomUUID();
        outboxPublisher.jobSubmitted(savedJob, idempotencyKey, correlationId);

        log.debug("Prepared submitted job intent for user {}: jobId#{}", profileId, savedJob.getId());
        return PreparedJobSubmission.created(savedJob.getId(), profileId);
    }

    private Job createSubmittedJob(
            Profile profile,
            JobSubmissionRequest request,
            OffsetDateTime now
    ) {
        var job = Job.builder()
                .id(UUID.randomUUID())
                .profile(profile)
                .status(JobStatus.SUBMITTED)
                .dockerImage(request.dockerImage())
                .executionCommand(request.executionCommand())
                .reqCpuCores(request.reqCpuCores())
                .reqRamGb(request.reqRamGb())
                .reqGpu(request.reqGpu() != null && request.reqGpu())
                .envVarsJson(request.envVars())
                .queuedAt(null)
                .activeLeaseExpiresAt(null)
                .currentLeaseReservedMinutes(0L)
                .leaseSequence(0L)
                .leaseSettled(false)
                .totalConsumedMinutes(0L)
                .leaseRenewalAttemptCount(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return jobRepository.save(job);
    }
}
