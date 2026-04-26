# 06. Nomad Dispatcher and Runner Contract

## Goal

Make Nomad dispatch an idempotent side effect of durable state, and define a stable contract between the control plane and runner containers.

Nomad owns execution and placement. Postgres owns product truth.

## Current Repository Baseline

- `NomadHttpDispatchClient` renders `nomad/job-template.hcl`, parses HCL through Nomad, then registers the job.
- `FairQueueDispatcherService` directly calls `NomadDispatchClient`.
- Nomad job IDs include user/job identifiers.
- MinIO upload URL and artifact object key are injected into the Nomad template.
- There is no explicit run spec object in MinIO.

## Dispatcher Target Flow

```text
RunQueued
-> dispatcher worker claims run
-> append RunDispatchRequested
-> register Nomad job idempotently
-> append RunDispatched or RunDispatchFailed
-> projection update
```

The dispatcher should be driven by outbox/work queue state, not the API request path.

## Database Additions

On `runs`:

- `dispatch_status`
- `dispatch_claimed_by`
- `dispatch_claimed_until`
- `nomad_job_id`
- `nomad_eval_id`
- `dispatch_attempt_count`
- `last_dispatch_error`

Indexes:

- `(status, dispatch_claimed_until, queued_at)` for claimable queued runs.
- Unique `nomad_job_id` where not null.

Optional:

- `nomad_mappings(run_id, nomad_job_id, eval_id, allocation_id, created_at, updated_at)`.

## Nomad Job Identity

Use deterministic, unique IDs:

```text
pcrm-run-<run_id>
```

Do not include mutable user-facing names in Nomad IDs.

Store:

- `run_id`
- `job_id`
- `user_id`
- `quota_reservation_id`
- `correlation_id`
- `resource_class`

as Nomad meta/environment where appropriate.

## Runner Contract

Dispatcher passes only metadata and MinIO references:

- `JOB_ID`
- `RUN_ID`
- `USER_ID`
- `TRACE_ID`
- `CORRELATION_ID`
- `QUOTA_RESERVATION_ID`
- `RESOURCE_CLASS`
- `SPEC_URI`
- `OUTPUT_PREFIX`
- `MANIFEST_OBJECT_KEY`

Runner responsibilities:

- fetch job spec from MinIO
- fetch inputs if supported
- execute workload
- stream stdout/stderr
- respect timeout/cancel signal
- upload outputs under run prefix
- write final manifest
- exit with workload outcome

Runner must not decide billing.

## Job Spec Object

Before dispatch, write a MinIO object:

```text
specs/<user_id>/<job_id>/<run_id>/job-spec.json
```

Contents:

- docker image
- command
- environment variables
- requested resources
- timeout
- resource class
- input references
- output prefix
- correlation IDs

The Nomad template should receive `SPEC_URI`, not full user spec details where avoidable.

## Backend Steps

1. Refactor dispatcher to select `runs` with `QUEUED` status.
2. Claim dispatch with `FOR UPDATE SKIP LOCKED` or conditional update:
   - only one dispatcher can claim a run
   - claims expire and can be retried
3. Append `RunDispatchRequested`.
4. Write job spec object to MinIO.
5. Render Nomad job template with deterministic run metadata.
6. Register Nomad job.
7. Store Nomad job/eval mapping.
8. Append `RunDispatched`.
9. On error:
   - append `RunDispatchFailed`
   - release quota if dispatch is final
   - retry only if failure is transient

## Template Changes

Update `nomad/job-template.hcl` to:

- use `pcrm-run-<run_id>` job ID
- include meta values for `job_id`, `run_id`, `quota_reservation_id`, `correlation_id`
- provide runner with `SPEC_URI` and output prefix
- keep resource limits explicit
- enforce timeout at Nomad task level when possible
- avoid persisting secrets or raw env vars in Postgres beyond approved spec storage policy

## Dispatch Idempotency

A retry of dispatch for the same run must not create duplicate compute.

Required rules:

- same run -> same Nomad job ID
- if Nomad says job already exists, fetch/verify it before treating as success
- never create a second Nomad job for a run unless a correction event explicitly supersedes the old mapping

## Tests

- Dispatcher claims a queued run once under concurrent workers.
- Dispatch retry uses the same Nomad job ID.
- Nomad registration failure marks dispatch failure and does not leave run as queued forever.
- Spec object key is deterministic per run.
- Nomad template contains run metadata and no missing placeholders.

## Acceptance Criteria

- Dispatch is fully decoupled from API submission.
- Every Nomad job maps to exactly one run.
- Dispatcher can crash after Nomad registration and recover idempotently.
- Runner receives enough metadata to execute and finalize artifacts without owning billing decisions.

