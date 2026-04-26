# 11. Periodic Reconciler

## Goal

Add periodic repair flows that compare Postgres projections with Nomad and MinIO reality, then append correction events.

The reconciler is required even with a Nomad stream because streams can disconnect, workers can crash, and object finalization callbacks can be missed.

## Reconciler Principles

- Never edit history.
- Append facts, corrections, and repair events.
- Update projections only as a consequence of repair events.
- Prefer idempotent, small repair tasks over one large repair job.
- Every correction must include reason, observed source, and correlation ID.

## Repair Scans

### Stuck Dispatches

Find runs:

- `DISPATCHING` too long
- dispatch claim expired
- no Nomad job exists

Actions:

- append `RunDispatchTimedOut`
- retry dispatch or fail and release reservation depending on attempt count

### Running Without Nomad Allocation

Find runs:

- status `RUNNING`
- allocation missing from Nomad
- no terminal event received

Actions:

- query Nomad job/alloc history
- append `RunMarkedLost` or correct to terminal state
- trigger quota finalization

### Nomad Allocation Without Run Mapping

Find Nomad jobs/allocations with PCRM metadata but no matching run projection.

Actions:

- append `OrphanNomadAllocationDetected`
- stop allocation if no durable run intent/reservation exists
- page/alert if compute was running without reservation

### Terminal Process Missing Manifest

Find runs:

- process succeeded
- status `FINALIZING`
- manifest missing beyond grace period

Actions:

- query MinIO manifest key
- if found, append `ArtifactManifestDiscovered`
- if missing, append `ArtifactFinalizationMissing`
- mark run failed if product policy says finalization timeout is terminal

### Stale Quota Reservations

Find reservations:

- active
- run terminal
- or reservation `expires_at` elapsed

Actions:

- append `QuotaReservationExpired` or trigger finalization
- update reservation/balance projections through quota service

### Projection Mismatches

Recalculate:

- quota balance from grants/reservations/usage ledger
- run status from latest domain event
- artifact status from manifest table/events

Actions:

- append `ProjectionMismatchDetected`
- rebuild projection row
- append `ProjectionRebuilt`

## Database Additions

Add `reconciliation_runs`:

- `id`
- `reconciler_name`
- `started_at`
- `finished_at`
- `status`
- `scanned_count`
- `repaired_count`
- `error_count`
- `last_error`

Add `reconciliation_findings`:

- `id`
- `reconciliation_run_id`
- `finding_type`
- `resource_type`
- `resource_id`
- `severity`
- `details JSONB`
- `repair_event_id`
- `created_at`

## Backend Steps

1. Add `reconciler` package.
2. Implement one scheduled component per scan type.
3. Add configuration:
   - scan intervals
   - age thresholds
   - batch sizes
   - max repairs per cycle
4. Build Nomad query client methods:
   - get job
   - get allocations by job
   - stop job/allocation for orphan cleanup
5. Build MinIO manifest existence/read methods.
6. Use domain event appender for all repairs.
7. Update projections through normal projection handlers.
8. Emit metrics and findings.

## Safety Rules

- Orphan compute cleanup may stop Nomad jobs, but only after confirming no durable run/reservation exists.
- Repair actions must be idempotent.
- Use conservative thresholds to avoid fighting normal eventual state propagation.
- Do not fail an entire scan because one repair fails.

## Tests

- Stale active reservation for terminal run is consumed/released.
- Missing manifest discovered in MinIO moves artifact projection forward.
- Running run with dead allocation becomes infra failed.
- Duplicate reconciler cycles do not append duplicate correction events.
- Projection rebuild produces expected balance from ledger.

## Acceptance Criteria

- Missed Nomad stream events are eventually repaired.
- Missed artifact finalization callbacks are eventually repaired.
- Stale quota reservations are visible and corrected.
- Operators can inspect what the reconciler changed and why.

