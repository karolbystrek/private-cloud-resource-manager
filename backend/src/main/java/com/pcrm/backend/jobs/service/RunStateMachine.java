package com.pcrm.backend.jobs.service;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.repository.RunRepository;
import com.pcrm.backend.nomad.NomadDispatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class RunStateMachine {

    private final RunRepository runRepository;
    private final JobRepository jobRepository;

    public Run markQueued(Run run, OffsetDateTime now, String resourceClass, long reservedMinutes) {
        run.setStatus(RunStatus.QUEUED);
        run.setResourceClass(resourceClass);
        run.setQueuedAt(now);
        run.setCurrentLeaseReservedMinutes(reservedMinutes);
        run.setActiveLeaseExpiresAt(now.plusMinutes(reservedMinutes));
        run.setLeaseSequence(1L);
        run.setLeaseSettled(false);
        return saveAndProject(run);
    }

    public Run markRejectedForInsufficientQuota(Run run, OffsetDateTime now, String reason) {
        run.setStatus(RunStatus.FAILED);
        run.setTerminalReason(reason);
        run.setProcessFinishedAt(now);
        run.setLeaseSettled(true);
        return saveAndProject(run);
    }

    public Run markDispatchRequested(Run run, OffsetDateTime now) {
        run.setStatus(RunStatus.DISPATCHING);
        run.setDispatchRequestedAt(now);
        run.setNomadJobId(run.getId().toString());
        run.setDispatchAttemptCount((run.getDispatchAttemptCount() == null ? 0L : run.getDispatchAttemptCount()) + 1);
        run.setLastDispatchError(null);
        return saveAndProject(run);
    }

    public Run markDispatchRetryRequested(Run run, OffsetDateTime now) {
        run.setDispatchRequestedAt(now);
        run.setNomadJobId(run.getId().toString());
        run.setDispatchAttemptCount((run.getDispatchAttemptCount() == null ? 0L : run.getDispatchAttemptCount()) + 1);
        run.setLastDispatchError(null);
        return saveAndProject(run);
    }

    public Run markDispatchAccepted(Run run, NomadDispatchResult dispatchResult, OffsetDateTime now) {
        run.setNomadJobId(dispatchResult.nomadJobId());
        run.setNomadEvalId(dispatchResult.nomadEvalId());
        run.setDispatchedAt(now);
        run.setStatus(RunStatus.SCHEDULING);
        run.setLastDispatchError(null);
        return saveAndProject(run);
    }

    public Run markDispatchFailed(Run run, String reason, OffsetDateTime now) {
        run.setLastDispatchError(reason);
        run.setStatus(RunStatus.INFRA_FAILED);
        run.setTerminalReason("NOMAD_DISPATCH_FAILED");
        run.setProcessFinishedAt(now);
        return saveAndProject(run);
    }

    public Run applyNomadTransition(Run run, RunStatus nextStatus, OffsetDateTime now, String terminalReason) {
        run.setStatus(nextStatus);
        if (nextStatus == RunStatus.RUNNING && run.getStartedAt() == null) {
            run.setStartedAt(now);
        }
        if ((nextStatus == RunStatus.FINALIZING || nextStatus == RunStatus.SUCCEEDED)
                && run.getProcessFinishedAt() == null) {
            run.setProcessFinishedAt(now);
        }
        if (nextStatus == RunStatus.SUCCEEDED && run.getFinalizedAt() == null) {
            run.setFinalizedAt(now);
        }
        if (terminalReason != null) {
            run.setTerminalReason(terminalReason);
        }
        if (isTerminal(nextStatus)) {
            run.setProcessFinishedAt(now);
        }
        return saveAndProject(run);
    }

    public Run markNomadStopped(Run run, OffsetDateTime now) {
        run.setStatus(RunStatus.CANCELED);
        run.setProcessFinishedAt(now);
        run.setTerminalReason("NOMAD_JOB_STOPPED");
        return saveAndProject(run);
    }

    public Run markLeaseRenewed(Run run, OffsetDateTime nextLeaseExpiresAt, long additionalReservedMinutes) {
        run.setCurrentLeaseReservedMinutes(run.getCurrentLeaseReservedMinutes() + additionalReservedMinutes);
        run.setActiveLeaseExpiresAt(nextLeaseExpiresAt);
        run.setLeaseSequence(run.getLeaseSequence() + 1L);
        run.setLeaseRenewalAttemptCount(run.getLeaseRenewalAttemptCount() + 1L);
        run.setLastLeaseRenewalError(null);
        run.setLeaseStopRequestedAt(null);
        return saveAndProject(run);
    }

    public Run markLeaseStopRequested(Run run, OffsetDateTime now, String reason) {
        run.setLeaseRenewalAttemptCount(run.getLeaseRenewalAttemptCount() + 1L);
        run.setLastLeaseRenewalError(reason);
        run.setLeaseStopRequestedAt(now);
        return saveAndProject(run);
    }

    public Run markTimedOut(Run run, OffsetDateTime now, String reason) {
        run.setStatus(RunStatus.TIMED_OUT);
        run.setTerminalReason(reason);
        run.setProcessFinishedAt(now);
        run.setLastLeaseRenewalError(null);
        return saveAndProject(run);
    }

    public Run recordLeaseStopFailure(Run run, OffsetDateTime now, String reason) {
        run.setLastLeaseRenewalError(reason);
        run.setLeaseStopRequestedAt(now);
        return saveAndProject(run);
    }

    public void markCurrentLeaseSettled(Run run, long consumedMinutes) {
        run.setTotalConsumedMinutes(run.getTotalConsumedMinutes() + consumedMinutes);
        run.setCurrentLeaseReservedMinutes(0L);
        run.setLeaseSettled(true);
        run.setActiveLeaseExpiresAt(null);
    }

    public Run saveAndProject(Run run) {
        var savedRun = runRepository.save(run);
        syncJobProjection(savedRun);
        return savedRun;
    }

    public boolean isTerminal(RunStatus status) {
        return status == RunStatus.SUCCEEDED
                || status == RunStatus.FAILED
                || status == RunStatus.CANCELED
                || status == RunStatus.TIMED_OUT
                || status == RunStatus.INFRA_FAILED;
    }

    private void syncJobProjection(Run run) {
        Job job = run.getJob();
        job.setStatus(run.getStatus());
        job.setCurrentRun(run);
        job.setQueuedAt(run.getQueuedAt());
        job.setStartedAt(run.getStartedAt());
        job.setFinishedAt(run.getProcessFinishedAt());
        job.setActiveLeaseExpiresAt(run.getActiveLeaseExpiresAt());
        job.setCurrentLeaseReservedMinutes(run.getCurrentLeaseReservedMinutes());
        job.setLeaseSequence(run.getLeaseSequence());
        job.setLeaseSettled(run.getLeaseSettled());
        job.setTotalConsumedMinutes(run.getTotalConsumedMinutes());
        jobRepository.save(job);
    }
}
