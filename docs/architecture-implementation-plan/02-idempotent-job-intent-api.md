# 02. Idempotent User-Intent API

## Goal

Make all mutating API workflows idempotent through one shared persistence contract.

The current implementation correctly requires `Idempotency-Key` for job submission, but stores submission idempotency directly on `jobs`. The target architecture needs reusable idempotency for job submission, cancellation, retry, schedules, admin quota changes, artifact finalization, and internal workflows.

## Current Repository Baseline

- `JobSubmissionIdempotencyService` normalizes UUID idempotency keys and calculates a SHA-256 request fingerprint.
- `jobs.idempotency_key` and `jobs.submission_fingerprint` dedupe job submissions.
- Frontend job submission generates and sends an `Idempotency-Key`.
- There is no reusable `idempotency_records` table.

## Database Migration

Add `idempotency_records`.

Fields:

- `id UUID PRIMARY KEY`
- `tenant_id UUID`
- `actor_type VARCHAR(40) NOT NULL`
- `actor_id UUID NOT NULL`
- `workflow VARCHAR(120) NOT NULL`
- `idempotency_key VARCHAR(128) NOT NULL`
- `request_fingerprint CHAR(64) NOT NULL`
- `status VARCHAR(30) NOT NULL`
- `locked_until TIMESTAMPTZ`
- `response_status INTEGER`
- `response_body JSONB`
- `resource_type VARCHAR(80)`
- `resource_id UUID`
- `created_at TIMESTAMPTZ NOT NULL`
- `updated_at TIMESTAMPTZ NOT NULL`

Constraints and indexes:

- Unique `(tenant_id, actor_type, actor_id, workflow, idempotency_key)`.
- Index `(status, locked_until)`.
- Check `status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_FINAL')`.

Use a nullable `tenant_id` only while the app has no tenant/org model. Keep the column so the future migration is additive.

## Shared Backend Contract

Create an `idempotency` package:

- `IdempotencyRecord`
- `IdempotencyRecordRepository`
- `IdempotencyService`
- `IdempotentWorkflow`
- `IdempotencyConflictException`
- `IdempotencyInProgressException`

Workflow algorithm:

1. Validate key format and length.
2. Canonicalize request payload.
3. Calculate `request_fingerprint`.
4. In a transaction, try to insert `IN_PROGRESS`.
5. If record exists:
   - same fingerprint + `COMPLETED`: return stored response.
   - same fingerprint + `IN_PROGRESS`: return current known result or `409/425` retry-later response.
   - different fingerprint: return conflict.
   - failed retryable: allow controlled retry if `locked_until` expired.
6. Execute workflow inside the durable transaction boundary when it only writes intent.
7. Store response status/body and resource reference.

Do not use idempotency keys as job IDs, run IDs, or Nomad job IDs.

## API Migration Steps

1. Keep existing `POST /jobs` behavior stable.
2. Add shared idempotency service behind `JobSubmissionService`.
3. Continue writing `jobs.idempotency_key` temporarily for backward compatibility.
4. After `idempotency_records` is used in production flows, plan a later migration to remove submission-specific columns from `jobs`.

## Required Workflows

### Job Submission

Workflow key: `job.submit`

Response resource:

- `resource_type = JOB`
- `resource_id = job_id`
- response body contains `jobId` and, after runs are introduced, `runId`.

### Job Cancellation

Workflow key: `job.cancel`

Persist intent only:

- append `RunCancellationRequested` or `JobCancellationRequested`
- write outbox
- return current request state

No direct Nomad stop in the HTTP request path.

### Job Retry

Workflow key: `job.retry`

Persist intent only:

- validate original job/run is retryable
- create new run
- append `RunRetryRequested` and `RunSubmitted`
- write outbox
- return new `runId`

### Admin Quota Grant

Workflow key: `admin.quota.grant.add`

Persist:

- quota grant row
- `QuotaGrantAdded`
- outbox message
- admin audit event metadata with actor, reason, interval, amount

### Artifact Finalization

Workflow key: `artifact.finalize`

Use for runner/control-plane finalization calls. Repeated finalization with the same manifest must return the same result. Same key with different manifest hash must conflict.

## Frontend Steps

1. Keep generating a new UUID idempotency key after each form mutation.
2. Reuse this pattern for cancel and retry buttons.
3. For Server Actions or route handlers that mutate state, require an idempotency key in the request.
4. Ensure error handling distinguishes:
   - duplicate completed request
   - conflicting key
   - request still in progress
   - insufficient quota

## Tests

Add backend tests for:

- Same key + same request returns same stored response.
- Same key + different request returns conflict.
- In-progress duplicate returns retry-later response.
- Completed idempotency survives application restart because the response is in Postgres.
- Job submission still creates exactly one job under concurrent duplicate requests.

Add frontend tests or component checks for:

- Submit sends `Idempotency-Key`.
- Changing form fields rotates the key.
- Cancel/retry operations include keys once those flows exist.

## Acceptance Criteria

- All new mutating APIs use `idempotency_records`.
- Job submission no longer depends only on `jobs.idempotency_key` for correctness.
- Duplicate client retries cannot create duplicate jobs, runs, quota grants, cancellations, or artifact finalizations.
- Idempotency behavior is documented in endpoint contracts.

