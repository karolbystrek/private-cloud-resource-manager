# 12. Observability, Auditing, and Operational Controls

## Goal

Make the system operable: every important state transition, quota movement, dispatch action, stream reconnect, and repair should be traceable.

## Correlation Fields

Use consistently:

- `job_id`
- `run_id`
- `user_id`
- `org_id` or tenant ID when introduced
- `nomad_job_id`
- `allocation_id`
- `quota_reservation_id`
- `trace_id`
- `idempotency_key`
- `event_id`
- `causation_id`
- `correlation_id`

Every domain event should include `correlation_id`. Every log line inside workers should include job/run/correlation IDs when available.

## Backend Steps

1. Add request correlation middleware:
   - accept `X-Correlation-Id` if valid
   - generate if missing
   - expose to logging context
2. Add event correlation propagation:
   - API intent event starts correlation
   - downstream events use causation/correlation links
3. Add structured logs for:
   - idempotency conflicts
   - quota reservations
   - quota finalization
   - dispatch claim/register/result
   - Nomad stream reconnects
   - artifact finalization
   - reconciler repairs
4. Add metrics with Micrometer:
   - counters for events by type
   - gauges for outbox lag and queue depth
   - timers for dispatch latency and time to allocation
   - counters for quota rejected/consumed/released
5. Add admin audit events:
   - quota grant added/revoked/corrected
   - policy changes
   - forced run cancellation
   - orphan allocation cleanup

## Dashboards

Minimum dashboards:

- Queue depth by resource class.
- Admission latency.
- Dispatch latency.
- Time to allocation.
- Time to first log.
- Run duration.
- Failure reasons.
- Quota reserved vs consumed.
- Quota rejections.
- Grant totals by role/admin.
- User balance distribution.
- Nomad stream reconnects and cursor lag.
- Reconciler corrections by type.
- Stale quota reservations.
- MinIO artifact failures.
- Outbox lag and retry counts.

## Alerts

Add alerts for:

- quota reservations stale
- quota projection mismatch
- runs stuck `DISPATCHING`
- runs `RUNNING` without Nomad allocation
- runs process-succeeded but manifest missing beyond grace period
- Nomad stream disconnected
- outbox lag growing
- dispatcher claim expiry high
- MinIO error rate high
- queue growing while capacity exists
- reconciler repair rate elevated
- any orphan allocation with no durable reservation

## Admin UX/API

Add operator endpoints or admin UI sections:

- outbox backlog summary
- recent reconciler findings
- stuck runs
- stale reservations
- quota grant history
- event timeline for a job/run

Protect these with admin authorization.

## Tests

- Correlation ID is generated and returned in API responses.
- Domain events include correlation/causation IDs.
- Admin quota changes create audit events with actor and reason.
- Metrics are emitted for outbox processing and quota reservation.

## Acceptance Criteria

- A production incident can be traced from UI request to events, outbox messages, Nomad job/allocation, quota ledger, and artifacts.
- Admin quota changes are auditable.
- Operators can detect stuck state before users report it.

