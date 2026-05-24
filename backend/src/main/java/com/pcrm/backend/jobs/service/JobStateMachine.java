package com.pcrm.backend.jobs.service;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class JobStateMachine {

    private final JobRepository jobRepository;

    public Job markQueued(Job job, OffsetDateTime now, long reservedMinutes, long leaseMinutes) {
        job.setStatus(JobStatus.QUEUED);
        job.setQueuedAt(now);
        job.setCurrentLeaseReservedMinutes(reservedMinutes);
        job.setActiveLeaseExpiresAt(now.plusMinutes(leaseMinutes));
        job.setLeaseSequence(1L);
        job.setLeaseSettled(false);
        return save(job);
    }

    public Job markRejectedForInsufficientQuota(Job job, OffsetDateTime now, String reason) {
        job.setStatus(JobStatus.FAILED);
        job.setTerminalReason(reason);
        job.setProcessFinishedAt(now);
        job.setLeaseSettled(true);
        return save(job);
    }

    public Job markDispatchRequested(Job job, OffsetDateTime now) {
        job.setStatus(JobStatus.DISPATCHING);
        job.setDispatchRequestedAt(now);
        return save(job);
    }

    public Job markDispatchRetryRequested(Job job, OffsetDateTime now) {
        job.setDispatchRequestedAt(now);
        return save(job);
    }

    public Job markDispatchAccepted(Job job, OffsetDateTime now) {
        job.setDispatchedAt(now);
        job.setStatus(JobStatus.SCHEDULING);
        return save(job);
    }

    public Job markDispatchFailed(Job job, OffsetDateTime now) {
        job.setStatus(JobStatus.INFRA_FAILED);
        job.setTerminalReason("NOMAD_DISPATCH_FAILED");
        job.setProcessFinishedAt(now);
        return save(job);
    }

    public Job applyNomadTransition(Job job, JobStatus nextStatus, OffsetDateTime now, String terminalReason) {
        job.setStatus(nextStatus);
        if (nextStatus == JobStatus.RUNNING && job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        if ((nextStatus == JobStatus.FINALIZING || nextStatus == JobStatus.SUCCEEDED)
                && job.getProcessFinishedAt() == null) {
            job.setProcessFinishedAt(now);
        }
        if (nextStatus == JobStatus.SUCCEEDED && job.getFinalizedAt() == null) {
            job.setFinalizedAt(now);
        }
        if (terminalReason != null) {
            job.setTerminalReason(terminalReason);
        }
        if (isTerminal(nextStatus)) {
            job.setProcessFinishedAt(now);
        }
        return save(job);
    }

    public Job markNomadStopped(Job job, OffsetDateTime now) {
        job.setStatus(JobStatus.CANCELED);
        job.setProcessFinishedAt(now);
        job.setTerminalReason("NOMAD_JOB_STOPPED");
        return save(job);
    }

    public Job markLeaseRenewed(Job job, OffsetDateTime nextLeaseExpiresAt, long additionalReservedMinutes) {
        job.setCurrentLeaseReservedMinutes(job.getCurrentLeaseReservedMinutes() + additionalReservedMinutes);
        job.setActiveLeaseExpiresAt(nextLeaseExpiresAt);
        job.setLeaseSequence(job.getLeaseSequence() + 1L);
        job.setLeaseRenewalAttemptCount(job.getLeaseRenewalAttemptCount() + 1L);
        job.setLastLeaseRenewalError(null);
        job.setLeaseStopRequestedAt(null);
        return save(job);
    }

    public Job markLeaseStopRequested(Job job, OffsetDateTime now, String reason) {
        job.setLeaseRenewalAttemptCount(job.getLeaseRenewalAttemptCount() + 1L);
        job.setLastLeaseRenewalError(reason);
        job.setLeaseStopRequestedAt(now);
        return save(job);
    }

    public Job markTimedOut(Job job, OffsetDateTime now, String reason) {
        job.setStatus(JobStatus.TIMED_OUT);
        job.setTerminalReason(reason);
        job.setProcessFinishedAt(now);
        job.setLastLeaseRenewalError(null);
        return save(job);
    }

    public Job markArtifactAvailable(Job job, OffsetDateTime now) {
        job.setStatus(JobStatus.SUCCEEDED);
        if (job.getProcessFinishedAt() == null) {
            job.setProcessFinishedAt(now);
        }
        job.setFinalizedAt(now);
        job.setTerminalReason(null);
        return save(job);
    }

    public Job markArtifactFailed(Job job, OffsetDateTime now, String reason) {
        job.setStatus(JobStatus.FAILED);
        if (job.getProcessFinishedAt() == null) {
            job.setProcessFinishedAt(now);
        }
        job.setFinalizedAt(now);
        job.setTerminalReason(reason);
        return save(job);
    }

    public Job recordLeaseStopFailure(Job job, OffsetDateTime now, String reason) {
        job.setLastLeaseRenewalError(reason);
        job.setLeaseStopRequestedAt(now);
        return save(job);
    }

    public void markCurrentLeaseSettled(Job job, long consumedMinutes) {
        job.setTotalConsumedMinutes(job.getTotalConsumedMinutes() + consumedMinutes);
        job.setCurrentLeaseReservedMinutes(0L);
        job.setLeaseSettled(true);
        job.setActiveLeaseExpiresAt(null);
    }

    public Job save(Job job) {
        return jobRepository.save(job);
    }

    public boolean isTerminal(JobStatus status) {
        return status == JobStatus.SUCCEEDED
                || status == JobStatus.FAILED
                || status == JobStatus.CANCELED
                || status == JobStatus.TIMED_OUT
                || status == JobStatus.INFRA_FAILED;
    }
}
