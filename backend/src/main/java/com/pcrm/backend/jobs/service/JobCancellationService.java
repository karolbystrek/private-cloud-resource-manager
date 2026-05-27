package com.pcrm.backend.jobs.service;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobDetailsResponse;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nomad.NomadJobControlClient;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.user.UserRole;
import com.pcrm.backend.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobCancellationService {

    private static final Set<JobStatus> CANCELLABLE_LOCAL_STATUSES = Set.of(
            JobStatus.SUBMITTED,
            JobStatus.QUEUED
    );
    private static final Set<JobStatus> CANCELLABLE_NOMAD_STATUSES = Set.of(
            JobStatus.DISPATCHING,
            JobStatus.SCHEDULING,
            JobStatus.RUNNING,
            JobStatus.FINALIZING
    );

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final QuotaAccountingService quotaAccountingService;
    private final ProfileRepository profileRepository;
    private final ObjectProvider<NomadJobControlClient> nomadJobControlClientProvider;
    private final TransactionTemplate transactionTemplate;

    public JobDetailsResponse cancelJob(UUID jobId, CustomUserDetails principal) {
        var plan = transactionTemplate.execute(_ -> prepareCancellation(jobId, principal));
        if (plan == null) {
            throw new IllegalStateException("Cancellation plan was not created");
        }

        if (plan.type() == CancellationPlanType.ALREADY_TERMINAL || plan.type() == CancellationPlanType.CANCELED_LOCALLY) {
            return toDetailsResponse(plan.job());
        }

        var nomadClient = nomadJobControlClientProvider.getIfAvailable();
        if (nomadClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Job cancellation is unavailable because Nomad integration is disabled"
            );
        }

        try {
            nomadClient.stopJob(plan.nomadJobId());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to cancel running job in Nomad");
        }

        var canceledJob = transactionTemplate.execute(_ -> finalizeNomadCancellation(jobId, principal));
        if (canceledJob == null) {
            throw new IllegalStateException("Failed to finalize job cancellation");
        }
        return toDetailsResponse(canceledJob);
    }

    private CancellationPlan prepareCancellation(UUID jobId, CustomUserDetails principal) {
        var job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId.toString()));
        assertAccess(job, principal);

        var status = job.getStatus();
        if (jobStateMachine.isTerminal(status)) {
            return CancellationPlan.alreadyTerminal(job);
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (CANCELLABLE_LOCAL_STATUSES.contains(status)) {
            settleCurrentLeaseIfNeeded(job, now, "Lease settled after user cancellation");
            return CancellationPlan.canceledLocally(jobStateMachine.markCanceledByUser(job, now));
        }

        if (CANCELLABLE_NOMAD_STATUSES.contains(status)) {
            return CancellationPlan.needsNomadStop(job, job.getId().toString());
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "Job cannot be canceled in its current state");
    }

    private Job finalizeNomadCancellation(UUID jobId, CustomUserDetails principal) {
        var job = jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId.toString()));
        assertAccess(job, principal);

        if (jobStateMachine.isTerminal(job.getStatus())) {
            return job;
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        settleCurrentLeaseIfNeeded(job, now, "Lease settled after user cancellation");
        return jobStateMachine.markCanceledByUser(job, now);
    }

    private void assertAccess(Job job, CustomUserDetails principal) {
        if (principal.role() == UserRole.ADMIN) {
            return;
        }
        if (job.getProfile().getId().equals(principal.id())) {
            return;
        }
        throw new ResourceNotFoundException("Job", "id", job.getId().toString());
    }

    private void settleCurrentLeaseIfNeeded(Job job, OffsetDateTime now, String reason) {
        if (Boolean.TRUE.equals(job.getLeaseSettled())) {
            return;
        }

        long reservedMinutes = Math.max(0L, job.getCurrentLeaseReservedMinutes());
        long consumedMinutes = calculateConsumedMinutes(job, now, reservedMinutes);

        quotaAccountingService.settleLeaseMinutes(job, reservedMinutes, consumedMinutes, reason);
        jobStateMachine.markCurrentLeaseSettled(job, consumedMinutes);
    }

    private long calculateConsumedMinutes(Job job, OffsetDateTime now, long reservedMinutes) {
        if (reservedMinutes <= 0 || job.getStartedAt() == null) {
            return 0L;
        }

        var leaseStart = job.getActiveLeaseExpiresAt() != null
                ? job.getActiveLeaseExpiresAt().minusMinutes(reservedMinutes)
                : job.getStartedAt();
        var effectiveStart = leaseStart.isAfter(job.getStartedAt()) ? leaseStart : job.getStartedAt();
        long elapsedSeconds = Math.max(0L, Duration.between(effectiveStart, now).getSeconds());
        long roundedUpMinutes = (elapsedSeconds + 59L) / 60L;
        return Math.min(reservedMinutes, roundedUpMinutes);
    }

    private JobDetailsResponse toDetailsResponse(Job job) {
        var email = profileRepository.findEmailForAuthUser(job.getProfile().getId()).orElse("");
        return JobDetailsResponse.from(job, email);
    }

    private enum CancellationPlanType {
        ALREADY_TERMINAL,
        CANCELED_LOCALLY,
        NEEDS_NOMAD_STOP
    }

    private record CancellationPlan(
            CancellationPlanType type,
            Job job,
            String nomadJobId
    ) {
        private static CancellationPlan alreadyTerminal(Job job) {
            return new CancellationPlan(CancellationPlanType.ALREADY_TERMINAL, job, null);
        }

        private static CancellationPlan canceledLocally(Job job) {
            return new CancellationPlan(CancellationPlanType.CANCELED_LOCALLY, job, null);
        }

        private static CancellationPlan needsNomadStop(Job job, String nomadJobId) {
            return new CancellationPlan(CancellationPlanType.NEEDS_NOMAD_STOP, job, nomadJobId);
        }
    }
}

