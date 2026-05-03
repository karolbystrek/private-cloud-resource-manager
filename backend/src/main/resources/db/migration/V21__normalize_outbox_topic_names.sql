UPDATE outbox
SET topic = CASE topic
    WHEN 'job.submitted' THEN 'JobSubmitted'
    WHEN 'run.submitted' THEN 'RunSubmitted'
    WHEN 'run.created' THEN 'RunCreated'
    WHEN 'run.lease.reserved' THEN 'RunLeaseReserved'
    WHEN 'job.queued' THEN 'JobQueued'
    WHEN 'run.queued' THEN 'RunQueued'
    WHEN 'run.dispatch.requested' THEN 'RunDispatchRequested'
    WHEN 'run.dispatched' THEN 'RunDispatched'
    WHEN 'run.started' THEN 'RunStarted'
    WHEN 'run.process.succeeded' THEN 'RunProcessSucceeded'
    WHEN 'run.process.failed' THEN 'RunProcessFailed'
    WHEN 'run.finalizing' THEN 'RunFinalizing'
    WHEN 'run.failed' THEN 'RunFailed'
    WHEN 'run.canceled' THEN 'RunCanceled'
    WHEN 'run.timed_out' THEN 'RunTimedOut'
    WHEN 'run.infra_failed' THEN 'RunInfraFailed'
    WHEN 'job.dispatch.requested' THEN 'JobDispatchRequested'
    WHEN 'job.dispatched' THEN 'JobDispatched'
    WHEN 'job.started' THEN 'JobStarted'
    WHEN 'job.finished' THEN 'JobFinished'
    WHEN 'quota.reserved' THEN 'QuotaReserved'
    WHEN 'quota.rejected' THEN 'QuotaRejected'
    WHEN 'quota.consumed' THEN 'QuotaConsumed'
    WHEN 'quota.released' THEN 'QuotaReleased'
    WHEN 'nomad.signal.received' THEN 'NomadSignalReceived'
    ELSE topic
END
WHERE topic IN (
    'job.submitted',
    'run.submitted',
    'run.created',
    'run.lease.reserved',
    'job.queued',
    'run.queued',
    'run.dispatch.requested',
    'run.dispatched',
    'run.started',
    'run.process.succeeded',
    'run.process.failed',
    'run.finalizing',
    'run.failed',
    'run.canceled',
    'run.timed_out',
    'run.infra_failed',
    'job.dispatch.requested',
    'job.dispatched',
    'job.started',
    'job.finished',
    'quota.reserved',
    'quota.rejected',
    'quota.consumed',
    'quota.released',
    'nomad.signal.received'
);
