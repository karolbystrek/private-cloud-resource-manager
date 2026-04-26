# 03. Jobs, Runs, Attempts, and State Projections

## Goal

Separate user intent from execution attempts.

The current `jobs` table combines:

- user request
- current execution attempt
- Nomad state
- lease accounting markers
- artifact URL

The target model should be:

```text
job = user intent
run = one execution attempt
run_attempt = low-level dispatch/allocation attempt when needed
current projections = mutable query models
domain events = immutable audit truth
```

## Database Migration

Add or evolve tables in additive phases.

### `jobs`

Keep as user intent.

Recommended columns:

- `id`
- `user_id`
- `status`
- `docker_image`
- `execution_command`
- `req_cpu_cores`
- `req_ram_gb`
- `resource_class`
- `timeout_minutes`
- `created_at`
- `updated_at`
- `current_run_id`

Remove only in later cleanup:

- direct lease fields
- direct Nomad allocation fields
- direct artifact URL fields

### `runs`

Add:

- `id UUID PRIMARY KEY`
- `job_id UUID NOT NULL REFERENCES jobs(id)`
- `user_id UUID NOT NULL REFERENCES users(id)`
- `run_number INTEGER NOT NULL`
- `status VARCHAR(40) NOT NULL`
- `resource_class VARCHAR(60) NOT NULL`
- `requested_timeout_minutes BIGINT NOT NULL`
- `quota_reservation_id UUID`
- `nomad_job_id VARCHAR(180)`
- `nomad_eval_id VARCHAR(180)`
- `nomad_allocation_id VARCHAR(180)`
- `queued_at TIMESTAMPTZ`
- `dispatch_requested_at TIMESTAMPTZ`
- `dispatched_at TIMESTAMPTZ`
- `started_at TIMESTAMPTZ`
- `process_finished_at TIMESTAMPTZ`
- `finalized_at TIMESTAMPTZ`
- `terminal_reason VARCHAR(120)`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

Constraints:

- Unique `(job_id, run_number)`.
- Unique `(nomad_job_id)` where not null.
- Unique `(nomad_allocation_id)` where not null.

### `run_attempts`

Add when dispatch retry detail is needed:

- `id`
- `run_id`
- `attempt_number`
- `status`
- `dispatch_claimed_by`
- `dispatch_claimed_until`
- `nomad_job_id`
- `nomad_eval_id`
- `nomad_allocation_id`
- `failure_reason`
- timestamps

This can be introduced after `runs` if MVP does not need multiple dispatch attempts per run.

### Current Projections

Add explicit projection tables or make existing tables the projection layer.

Recommended:

- `jobs_current`
- `runs_current`

If keeping current JPA entities on `jobs`/`runs`, document that these are mutable projections backed by immutable domain events.

## State Model

Use a broader run status enum:

```text
CREATED
SUBMITTED
QUOTA_CHECKING
QUOTA_RESERVED
QUEUED
DISPATCHING
SCHEDULING
PENDING_RESOURCES
STARTING
RUNNING
FINALIZING
SUCCEEDED
FAILED
CANCELING
CANCELED
TIMED_OUT
INFRA_FAILED
RETRYING
```

Map old statuses:

- `QUEUED` -> `QUEUED`
- `PENDING` -> `SCHEDULING` or `PENDING_RESOURCES`
- `RUNNING` -> `RUNNING`
- `COMPLETED` -> initially `FINALIZING`, then `SUCCEEDED` only after manifest verification
- `FAILED` -> `FAILED` or `INFRA_FAILED` depending on Nomad/task reason
- `OOM_KILLED` -> `FAILED` with `terminal_reason = OOM_KILLED`
- `LEASE_EXPIRED` -> `TIMED_OUT` or `FAILED` with `terminal_reason = LEASE_EXPIRED`
- `STOPPED` -> `CANCELED` or `INFRA_FAILED` depending on cause

## Domain Events to Add

For job aggregate:

- `JobSubmitted`
- `JobCancellationRequested`
- `JobRetryRequested`

For run aggregate:

- `RunCreated`
- `RunSubmitted`
- `RunQuotaChecking`
- `RunQuotaReserved`
- `RunQueued`
- `RunDispatchRequested`
- `RunDispatched`
- `RunPendingResources`
- `RunStarted`
- `RunProcessSucceeded`
- `RunProcessFailed`
- `RunFinalizing`
- `RunFinalized`
- `RunSucceeded`
- `RunFailed`
- `RunCancellationRequested`
- `RunCanceled`
- `RunTimedOut`
- `RunInfraFailed`
- `RunStateReconciled`

## Backend Steps

1. Add JPA entities/repositories for `Run` and optionally `RunAttempt`.
2. Update submission transaction:
   - create `Job`
   - create first `Run`
   - append `JobSubmitted`
   - append `RunSubmitted`
   - write outbox
3. Update query services:
   - job list shows latest/current run summary
   - job details returns job plus current run
4. Update dispatcher:
   - selects runs, not jobs
   - writes Nomad mapping to run
5. Update Nomad listener:
   - resolves run by `nomad_job_id` or allocation ID
   - appends run events
   - updates run projection
6. Update frontend DTOs:
   - include `runId`
   - display run status instead of old job-only status
   - keep existing URLs compatible by resolving current run for a job

## Migration Strategy

1. Add `runs` table.
2. Backfill one run per existing job:
   - `run_number = 1`
   - copy timestamps/status/resource fields
   - set `jobs.current_run_id`
3. Update backend writes to create/update runs.
4. Keep read compatibility for old job detail endpoints.
5. After all flows use runs, remove execution fields from `jobs` in a cleanup release.

## Tests

Backend:

- Submitting a job creates exactly one job and one run.
- Retrying a terminal job creates a second run and does not mutate the first run history.
- Current job status derives from current run status.
- Backfill migration maps existing jobs safely.
- Invalid state transitions are rejected or ignored idempotently.

Frontend:

- Job list still works after response includes current run.
- Job detail shows current run lifecycle accurately.
- Retry/cancel controls use run-aware state.

## Acceptance Criteria

- User intent and execution attempt data are no longer conflated.
- Existing jobs are migrated to runs without losing user-visible history.
- Retries create new runs, old runs remain queryable.
- Terminal success requires the later artifact finalization step, not just Nomad process completion.

