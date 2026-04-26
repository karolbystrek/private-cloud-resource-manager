# 10. Quota Finalization on Run Completion

## Goal

Finalize quota from trusted runtime facts when a run ends:

```text
actual usage < reservation -> consume actual, release remainder
actual usage = reservation -> consume all
actual usage > reservation -> consume reservation and append correction/overage policy event
```

This step closes the billing loop while preserving the invariant: no compute without prepaid quota reservation.

## Current Repository Baseline

- `NomadEventStreamListener` settles current lease when it sees terminal allocation/job events.
- `QuotaAccountingService.settleLeaseMinutes` consumes/refunds integer minutes.
- Settlement is tied to the old single-job lease fields.

## Target Flow

1. Nomad stream or reconciler appends terminal process event:
   - `RunProcessSucceeded`
   - `RunProcessFailed`
   - `RunTimedOut`
   - `RunInfraFailed`
   - `RunCanceled`
2. Quota finalization worker consumes terminal run event.
3. Worker locks run, reservation, and quota balance.
4. Calculates actual runtime from trusted timestamps.
5. Applies product charge policy.
6. Appends quota usage/release events.
7. Updates quota projections.
8. Appends `RunQuotaFinalized`.

## Charge Policy

Recommended policy:

- User-code success: charge actual runtime.
- User-code failure: charge actual runtime.
- User cancellation: charge actual runtime.
- Timeout: charge actual runtime up to reservation.
- Infra failure before workload starts: release reservation.
- Infra failure after partial useful runtime: choose one policy and encode reason explicitly.

All policy decisions must be represented as reason codes.

Reason codes:

- `USER_WORKLOAD_SUCCEEDED`
- `USER_WORKLOAD_FAILED`
- `USER_CANCELED`
- `TIMEOUT`
- `INFRA_FAILURE_BEFORE_START`
- `INFRA_FAILURE_AFTER_START`
- `DISPATCH_FAILED`
- `CORRECTION`

## Runtime Calculation

Use:

- `started_at` from trusted Nomad/runner signal
- terminal timestamp from Nomad/runner/reconciler
- resource class multiplier from reservation

Formula:

```text
raw_runtime_seconds = max(0, finished_at - started_at)
wall_clock_minutes = ceil(raw_runtime_seconds / 60)
compute_minutes = wall_clock_minutes * resource_class_multiplier
```

Cap by reservation unless explicit overage policy exists.

## Backend Steps

1. Create `QuotaFinalizationWorker`.
2. Consume terminal run events.
3. Dedupe with `event_consumer_dedupe`.
4. Lock:
   - run row
   - quota reservation row
   - user quota balance row
5. Skip if reservation already terminal and run quota finalized.
6. Calculate usage.
7. Append:
   - `QuotaConsumed`
   - `QuotaReleased` if remainder exists
   - `QuotaUsageCorrected` when reconciling a mismatch
   - `RunQuotaFinalized`
8. Update projections:
   - reservation status
   - balance current
   - run consumed compute minutes
9. Trigger artifact finalization path if process succeeded.

## Failure Handling

If finalization worker crashes:

- reservation remains active
- event remains unprocessed or outbox retry fires
- reconciler later detects stale terminal run with active reservation

Never silently drop a reservation.

## Interaction With Success

Quota finalization and artifact finalization are both required, but they are separate:

- process terminal event finalizes quota
- artifact manifest finalizes outputs
- run becomes `SUCCEEDED` only when process succeeded and artifact finalized

Failed/canceled/timed-out runs can be terminal without artifact success if policy allows partial artifacts.

## Tests

- Successful 61-second run consumes 2 wall-clock minutes times multiplier.
- Early finish releases unused reservation.
- Cancellation charges only runtime after start.
- Infra failure before start releases all quota.
- Duplicate terminal event does not double-consume.
- Stale active reservation after terminal run is repaired by reconciler.

## Acceptance Criteria

- Quota finalization no longer lives inside the Nomad stream listener.
- Runtime billing is explicit, integer-minute based, and reason-coded.
- Reserved quota is always consumed or released for terminal runs.
- Usage ledger and balance projection can be reconciled.

