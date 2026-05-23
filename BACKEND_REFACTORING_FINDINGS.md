# Backend Refactoring Findings

This document captures findings from a backend and schema review focused on the core product goal: an open source system
universities can use to share batch compute resources safely through Nomad.

The target shape should be boring and reliable:

- users submit a container job;
- quota is reserved before compute can start;
- the job runs in Nomad;
- logs are visible in the job details view;
- outputs are placed in one documented directory, compressed, uploaded, and downloaded reliably;
- operators can observe and recover the system without understanding every internal edge case.

## Executive Summary

The current backend has the right ingredients, but too many of them are active at the same time. Job state, run state,
quota reservation state, event state, outbox state, Nomad state, and artifact state are not separated cleanly enough.
Several fields and tables look like they were added for a future architecture, but the current implementation only
partially uses them.

Most important reliability gaps:

1. **No visible lease renewal or lease expiry enforcer exists in the backend.**
   The system reserves an initial 15-minute lease, but I did not find a worker that renews leases or kills active Nomad
   jobs when a lease expires. This directly affects the "No Unbilled Compute" invariant.

2. **Artifact success is not durable application state.**
   The Nomad poststop task uploads `output.zip`, but the backend has no artifact table, no manifest, no object-existence
   check before showing download, no upload callback, and no clear distinction between "process succeeded" and "artifact
   finalized".

3. **Logs depend on Nomad availability and retention.**
   The SSE endpoint streams from Nomad allocation logs directly. This is simple, but completed-job logs may disappear.
   There is no durable log index, no archival path, and no backend-side log state.

4. **`jobs` and `runs` duplicate important mutable state.**
   Status, lease fields, timestamps, and consumed minutes exist on both tables. Several services manually copy run state
   into the job row. This is a coupling hotspot.

5. **The event/outbox architecture is only partially the source of truth.**
   Events are appended, but most business state is still mutated directly by workers and Nomad listeners. This gives the
   complexity of event-driven design without fully gaining replayability or clean projections.

## Current Backend Flow

### Job Submission

Observed flow:

1. `JobsResource.submitJob` accepts `POST /jobs`.
2. `JobSubmissionService` wraps submission in `IdempotencyService`.
3. `JobSubmissionPersistenceService` creates a `jobs` row and a first `runs` row.
4. `JobRunEventPublisher` appends `JobSubmitted` and `RunSubmitted`.
5. `OutboxPoller` delivers `RunSubmitted`.
6. `RunAdmissionWorker` reserves the initial lease and moves the run to `QUEUED`.
7. `FairQueueDispatcherService` picks queued runs and registers Nomad jobs.
8. `NomadEventStreamListener` reads Nomad events and updates run/job status.

This is workable, but it is difficult to reason about because creation, admission, dispatch, Nomad event ingestion,
quota settlement, and job projection all mutate overlapping fields.

### Logs

Observed flow:

- `GET /jobs/{id}/logs/stream` returns an SSE stream.
- `JobLogsService` resolves the current run and Nomad job id.
- It asks Nomad for allocations and streams stdout/stderr from the selected allocation.
- The frontend reconnects with byte offsets and keeps up to 2000 lines in memory.

Strengths:

- Simple and mostly stateless.
- Good enough for live logs while Nomad keeps them available.

Risks:

- For completed jobs, logs can become unavailable due to Nomad retention.
- There is no durable backend storage for logs.
- The frontend waits for active states and only opens logs once status leaves `SUBMITTED`, `QUEUED`, `DISPATCHING`, or
  `SCHEDULING`, so early diagnostics may be hidden.
- Allocation resolution is heuristic when Nomad metadata is incomplete.

### Artifacts

Observed flow:

- `StorageService.buildArtifactObjectKey` always uses `artifacts/<user_id>/<job_id>/output.zip`.
- `NomadHttpDispatchClient` injects a presigned upload URL into the Nomad job.
- `nomad/job-template.hcl` runs a poststop `artifact-uploader` task.
- The uploader zips `$NOMAD_ALLOC_DIR/data` into `/tmp/output.zip` and uploads it.
- The frontend asks for a presigned download URL after a job is no longer active.

Strengths:

- The user contract can be simple: write outputs to `$NOMAD_ALLOC_DIR/data`.
- The backend does not need to receive large files directly.

Risks:

- The user-facing job form does not document the output directory contract.
- There is no durable artifact status.
- The backend returns a download URL without checking whether the object exists.
- Artifacts are keyed by job id, not run id, so retries or future multiple runs can overwrite or confuse output.
- A run is marked `SUCCEEDED` from Nomad allocation status before artifact upload is verified.
- The upload presigned URL is generated at dispatch time with a default 15-minute TTL. Long-running jobs can outlive the
  upload URL, making artifact upload fail after the workload finishes.

## Schema Findings

### Tables That Are Core

These tables support the required product behavior and should remain, though some fields can be simplified:

- `profiles`
- `jobs`
- `runs`
- `quota_policy`
- `user_quota_balance_current`
- `quota_reservations`
- `quota_usage_ledger`
- `idempotency_records`
- `nomad_event_stream_cursor`

### Tables That May Be Overbuilt For Current Needs

These are defensible, but currently add complexity beyond what the implementation fully uses:

- `domain_events`
- `aggregate_sequences`
- `outbox`
- `event_consumer_dedupe`
- `quota_grants`
- `user_quota_override`
- `nodes`

They are not necessarily wrong. The question is whether the project wants a full event-sourced/event-log architecture or
a simpler transactional state-machine architecture with an outbox only for side effects.

### Duplicated Mutable Fields

`jobs` and `runs` both contain:

- `status`
- `queued_at`
- `started_at` / `process_finished_at` or `finished_at`
- `active_lease_expires_at`
- `current_lease_reserved_minutes`
- `lease_sequence`
- `lease_settled`
- `total_consumed_minutes`

Recommendation:

- Make `runs` the source of truth for execution state.
- Make `jobs` immutable job intent plus `current_run_id`.
- Either remove duplicated job fields or explicitly treat them as a read projection maintained by one projection
  component, not manually copied in every worker.

### Missing Artifact State

There is no table for artifacts. Add a small durable table before adding more artifact features:

```sql
job_artifacts
- id
- job_id
- run_id
- profile_id
- status -- PENDING, UPLOADING, AVAILABLE, MISSING, FAILED
- object_key
- size_bytes
- checksum_sha256
- created_at
- finalized_at
- failure_reason
```

If keeping one zip per run, the object key should be run-scoped:

```text
artifacts/<profile_id>/<job_id>/<run_id>/output.zip
```

## Architecture Findings

### Event-Driven Design Needs A Narrower Role

Right now events are used as both audit trail and internal work queue. State changes still happen directly in services.
This creates many moving parts:

- aggregate sequence allocation;
- domain event append;
- outbox write;
- outbox polling;
- handler registry;
- consumer dedupe;
- direct JPA mutation;
- manual job projection sync.

Recommendation:

- Keep an outbox for asynchronous side effects.
- Do not try to make every state change event-sourced unless you need replay.
- Use a single transactional state-machine service for run transitions.
- Emit events after state transitions for observability and side effects.

Example target boundary:

- `RunStateMachine`: only place allowed to change `runs.status`, lease fields, and terminal fields.
- `QuotaService`: only place allowed to reserve, renew, consume, and release quota.
- `NomadGateway`: only place allowed to call Nomad.
- `ArtifactService`: only place allowed to create/finalize artifact records and presigned URLs.
- `LogService`: only place allowed to stream or retrieve logs.

### Missing Lease Enforcer

The repository contains initial lease reservation and terminal settlement, but not a complete lease lifecycle.

Required for "No Unbilled Compute":

1. Before dispatch, reserve the initial lease.
2. While running, renew in 15-minute chunks before expiry.
3. If renewal fails or quota is unavailable, request Nomad stop/kill before the current lease expires.
4. If the backend is down, a restarted worker must find expired active leases and stop the corresponding Nomad jobs.
5. Settlement must be idempotent and guarded by row locks.

Minimum table/query support:

- index active runs by `active_lease_expires_at`;
- query non-terminal runs where `active_lease_expires_at <= now + safety_window`;
- store renewal attempts and last renewal error, either on `runs` or a dedicated lease table.

### Dispatch Fairness Is Probably Premature

`FairQueueDispatcherService` uses in-memory user deficits and role weights. This adds complexity but is not durable
across workers, restarts, or horizontal scaling.

For a first reliable open source version, consider replacing it with:

- simple FIFO by `queued_at`;
- hard resource-fit check;
- one database claim query with `FOR UPDATE SKIP LOCKED`;
- later add fair scheduling after the core lifecycle is reliable.

If fairness remains, its state should be persisted or derived from durable quota/usage data, not stored in a local
`ConcurrentHashMap`.

### Node Table Is Mostly A Cache

`nodes` stores many Nomad fields. The dispatcher only needs available aggregate CPU/RAM. Keeping detailed node metadata
can be useful for admin observability, but it should be treated as a cache of Nomad, not core scheduling authority.

Simplification options:

- ask Nomad for node capacity when dispatching;
- or keep a smaller `nodes` projection with only fields required for scheduling and UI;
- avoid letting stale node rows block dispatch without a reconciliation story.

## Reliability Gaps To Address First

### 1. Build A Real Run State Machine

Define allowed transitions and centralize them.

Suggested states:

```text
SUBMITTED
ADMITTED
QUEUED
DISPATCHING
SCHEDULING
RUNNING
FINALIZING
SUCCEEDED
FAILED
CANCELED
TIMED_OUT
INFRA_FAILED
```

Questions to answer:

- Is `QUEUED` before or after quota reservation? Prefer after reservation.
- Is `SUCCEEDED` process success or artifact success? Prefer artifact success for user-facing success.
- Is `FINALIZING` where artifact upload/manifest verification happens? It should be.

### 2. Add Lease Renewal And Expiry Enforcement

This is the highest-priority backend gap.

Design target:

- `LeaseWorker` runs frequently.
- It locks expiring active runs.
- It renews quota if possible.
- It updates `active_lease_expires_at` and increments `lease_sequence`.
- If renewal fails, it stops the Nomad job and marks the run terminal when confirmed or after timeout.

Avoid relying only on Nomad terminal events for settlement. Nomad events can be delayed, lost, or unavailable during
outages.

### 3. Make Artifact Finalization Durable

Recommended contract for users:

```sh
mkdir -p "$NOMAD_ALLOC_DIR/data"
python my_program.py --output "$NOMAD_ALLOC_DIR/data"
```

Better user-facing alias:

```sh
mkdir -p "$OUTPUT_DIR"
python my_program.py --output "$OUTPUT_DIR"
```

Set `OUTPUT_DIR=$NOMAD_ALLOC_DIR/data` in the Nomad task environment.

Backend changes:

- create an artifact record before dispatch;
- upload to a run-scoped object key;
- after upload, verify object exists;
- mark artifact `AVAILABLE`;
- only then mark run `SUCCEEDED`;
- show download only when artifact status is `AVAILABLE`.

Nomad template changes:

- avoid generating the upload URL too early if jobs may run longer than the URL TTL;
- either use an internal endpoint to request an upload URL at poststop time, or make the artifact uploader use service
  credentials with a narrow object key policy;
- emit or call back artifact finalization status.

### 4. Decide Log Retention Strategy

Two viable options:

Option A: keep direct Nomad log streaming.

- Simpler.
- Accept that logs may disappear after completion.
- Make artifact download the durable result.
- UI should say logs are live/temporary.

Option B: persist logs.

- Add a log shipper or backend tailer.
- Store compressed logs in object storage per run.
- Serve live SSE from Nomad and completed logs from storage.

For a university batch system, Option A is acceptable if clearly documented and artifact output is reliable.

## Simplification Opportunities

### Remove Submission-Specific Idempotency Fields From `jobs`

`jobs.idempotency_key` and `jobs.submission_fingerprint` duplicate `idempotency_records`.

Recommendation:

- Keep `idempotency_records` as the generic idempotency mechanism.
- Remove job-specific idempotency columns once no frontend/backend code depends on them.

### Reduce Quota Grant Complexity

The current quota model has:

- policies;
- overrides;
- grants;
- reservations;
- balance current;
- ledger.

For a simple prepaid university system, the minimum reliable model is:

- `user_quota_balance_current` for fast balance;
- `quota_reservations` for active leases;
- `quota_usage_ledger` for append-only audit;
- `quota_policy` for default monthly grants.

`quota_grants` and `user_quota_override` are useful only if admins truly need historical grant records and per-user
policy overrides. Otherwise, they increase mental load.

### Replace `env_vars_json TEXT` With `JSONB`

`jobs.env_vars_json` is stored as `TEXT`. Since the database is PostgreSQL, use `JSONB` if keeping env vars in the
database. This improves validation/querying and avoids manual serialization/deserialization.

### Avoid Long-Running SSE Threads Per Client

`JobLogsService` uses `Executors.newCachedThreadPool()` and one blocking task per SSE connection. That is simple, but
can grow without bound under many users.

Options:

- set a bounded executor;
- set SSE timeout and heartbeat policy;
- move to WebFlux only if the rest of the stack justifies it;
- keep MVC but enforce connection limits.

### Make Nomad Job ID Format One Thing

The code supports several Nomad job id patterns, including UUID, `-run#`, and `-job#`. Current dispatch sets
`nomadJobId = run.id.toString()`.

Recommendation:

- Standardize on `run.id` as the Nomad job id.
- Remove legacy parsing branches after migration.

## Observability Recommendations

The backend already has Actuator and structured concepts like correlation ids, but observability is not complete.

Add:

- metrics for job state transitions;
- metrics for outbox lag and retry count;
- metrics for lease renewals, renewal failures, and forced kills;
- metrics for Nomad dispatch latency and failures;
- metrics for artifact upload success/failure/missing;
- logs that always include `jobId`, `runId`, `profileId`, `nomadJobId`, and `correlationId`;
- admin endpoint or dashboard for stuck runs and stuck outbox messages.

Stuck-run checks should detect:

- `DISPATCHING` too long;
- `SCHEDULING` too long;
- `RUNNING` with expired lease;
- terminal Nomad allocation but non-terminal database run;
- `SUCCEEDED` process but missing artifact;
- artifact exists but database says missing.

## Suggested Refactoring Roadmap

### Phase 1: Protect The Billing Invariant (Completed)

- Add lease renewal worker.
- Add lease expiry killer/enforcer.
- Add reconciliation for active runs on startup.
- Add tests around reservation, renewal, expiry, dispatch failure, and terminal settlement.

### Phase 2: Simplify Execution State (Completed)

- Make `runs` the execution source of truth.
- Remove or isolate manual `syncJobProjection` calls.
- Create a `RunStateMachine` and route all transitions through it.
- Standardize Nomad job id on `run.id`.

### Phase 3: Unify Job And Run (Completed)

- Collapse backend `Job` and `Run` into one execution entity.
- Treat one user submission as one Nomad job and one billable execution.
- Move execution fields from `runs` onto `jobs`: Nomad ids, dispatch metadata, lease fields, terminal fields, and
  consumed minutes.
- Remove the `Run` entity, `runs` table, `RunRepository`, `RunStateMachine`, run-specific events, and run-specific
  DTO/API fields.
- Use `jobs.id` as the Nomad job id.
- Point quota reservations, usage ledger entries, artifacts, logs, and events directly at `jobs`.
- If users rerun the same image/command, create a new `jobs` row instead of a child run.
- Add a Flyway migration that backfills job execution fields from the current run, rewires foreign keys, and drops
  run-only schema after validation.

### Phase 4: Make Artifacts First-Class (Completed)

- Add `job_artifacts`.
- Use job-scoped object keys.
- Add `OUTPUT_DIR` to the workload environment.
- Verify artifact existence before exposing download.
- Keep jobs in `FINALIZING` until artifact status is known.

### Phase 5: Reduce Event Complexity (Completed)

- Decide whether `domain_events` are an audit log or a true event-sourced model.
- If audit log: remove aggregate sequence strictness unless needed.
- Keep outbox for side effects and async workers.
- Remove unused topics and future-only event constants.

### Phase 6: Make Scheduling Boring

- Replace in-memory fair queue deficits with a durable simple claim loop.
- Add fairness later only if real usage shows starvation.
- Treat node data as a cache or query Nomad directly.

## High-Value Tests To Add Later

Do not start with broad integration tests. Start with invariant tests:

- submitting the same idempotency key twice returns the same job;
- submitting the same key with a different body returns conflict;
- job admission cannot reserve more quota than available;
- dispatch failure releases the initial lease;
- terminal success consumes only elapsed reserved minutes and releases the rest;
- expired lease causes Nomad stop/kill;
- artifact download is unavailable before artifact finalization;
- artifact upload failure does not produce user-facing `SUCCEEDED`;
- outbox handler retry does not duplicate quota reservations.

## Recommended Product Contract For Users

Document this prominently in the job submission UI:

```sh
mkdir -p "$OUTPUT_DIR"
your-command --output "$OUTPUT_DIR"
```

System-provided environment variables should include:

- `JOB_ID`
- `RUN_ID`
- `OUTPUT_DIR`

The service should promise:

- everything under `$OUTPUT_DIR` is compressed after the workload exits;
- the archive is available from the job details page after finalization;
- logs are live and may be temporary unless log archival is enabled.

********

## Bottom Line

The next improvement should not be more features. The next improvement should be making the lifecycle smaller and
stricter:

```text
submit -> reserve lease -> dispatch -> run -> renew/kill -> finalize artifact -> settle quota -> expose result
```

Once that path is centralized and observable, the surrounding features can be judged clearly. Until then, the system has
avoidable complexity in exactly the places where correctness matters most.
