# 05. Admission Worker and Transactional Quota Reservation

## Goal

Move admission into an event-driven worker that reserves quota transactionally before any run can reach dispatch.

The API request path should record intent and return IDs. Admission decides whether a run is allowed to enter the queue.

```text
RunSubmitted
-> admission worker
-> lock quota balance
-> QuotaReserved or QuotaRejected
-> RunQueued or RunFailed
```

## Current Repository Baseline

- `JobSubmissionPersistenceService` creates a `QUEUED` job and reserves the first 15-minute lease in the submission transaction.
- `FairQueueDispatcherService` polls `QUEUED` jobs and dispatches directly.
- This already protects against unreserved compute, but it mixes request handling, admission, and queueing.

## Target Behavior

API request:

1. Validate user request.
2. Create job and run.
3. Append `JobSubmitted` and `RunSubmitted`.
4. Write outbox row.
5. Return `job_id` and `run_id`.

Admission worker:

1. Consumes `RunSubmitted`.
2. Locks run row and quota balance.
3. Calculates max reservation from timeout and resource class multiplier.
4. If quota is available:
   - creates `quota_reservation`
   - appends `QuotaReserved`
   - appends `RunQueued`
   - updates projections
5. If quota is unavailable:
   - appends `QuotaRejected`
   - appends `RunFailed`
   - updates projections

## Database Needs

Add queue-oriented fields if not already in `runs`:

- `admission_status`
- `admitted_at`
- `rejected_at`
- `rejection_reason`
- `queue_priority`
- `queued_at`

Add indexes:

- `(status, queued_at, created_at)` for runnable queued runs.
- `(user_id, status)` for per-user active/queued limits.

Optional but recommended:

- `run_queue_current` projection with one row per queued run.
- `run_admission_claims` if admission workers need explicit claim rows.

## Backend Steps

1. Create `RunAdmissionWorker`.
2. Subscribe through outbox polling to `run.submitted`.
3. Add a claim pattern:
   - select run by ID `FOR UPDATE`
   - skip if run already admitted/rejected
   - set transient claim metadata if workers can run concurrently
4. Validate run can still be admitted:
   - user exists and is active
   - job/run not canceled
   - requested resources are within configured maximums
   - timeout is within product policy
5. Resolve resource class:
   - start with `cpu` for existing forms
   - map GPU/high-memory later from request fields
6. Reserve quota:
   - call the new quota reservation service
   - lock current balance
   - write reservation and events in the same transaction
7. Queue admitted run:
   - status `QUEUED`
   - append `RunQueued`
   - update queue projection
8. Reject unaffordable run:
   - status `FAILED`
   - terminal reason `INSUFFICIENT_QUOTA`
   - append `QuotaRejected` and `RunFailed`
   - return clear problem detail to the UI when submission waits for admission, or show rejected state when admission is async

## Fair Queue Integration

Refactor `FairQueueDispatcherService` to operate on runs.

Candidate scoring should use:

- queued age
- user role weight
- usage ratio from quota balance
- dominant resource share
- configured anti-starvation boost

Keep fairness separate from billing:

- billing/quota uses normalized compute minutes
- fairness cost uses dominant cluster resource share

## API Response Choice

Recommended MVP behavior:

- `POST /jobs` returns `202 Accepted` or `201 Created` with `jobId` and `runId` immediately after durable intent.
- UI shows `SUBMITTED` or `QUOTA_CHECKING`.
- Admission result arrives through polling/realtime.

Compatibility option:

- Keep returning `201 Created`, but do not imply dispatch has happened.

Do not dispatch directly in request path.

## Tests

- Submitted run becomes queued when quota is sufficient.
- Submitted run becomes failed/rejected when quota is insufficient.
- Concurrent admission workers cannot reserve the same balance twice.
- Cancel before admission prevents reservation.
- Admission is idempotent when the same `RunSubmitted` event is processed twice.
- Fair queue selection still excludes resource-impossible runs.

## Acceptance Criteria

- API submission persists intent only.
- No run enters `QUEUED` without a durable active quota reservation.
- Admission can be retried safely after worker crash.
- Dispatcher only sees runs that have already passed quota admission.

