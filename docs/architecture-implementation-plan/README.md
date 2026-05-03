# Architecture Implementation Plan

This plan translates the target architecture into an execution roadmap for the current repository state.

It does not treat the target design as a literal rewrite request. The current system already contains useful pieces:

- Spring Boot backend, Next.js frontend, PostgreSQL migrations, Nomad integration, MinIO presigned artifact URLs.
- Job submission with `Idempotency-Key` stored on `jobs`.
- Quota-window accounting with `quota_policy`, `user_quota_override`, `quota_window`, and append-only `quota_ledger`.
- A fair queue dispatcher that dispatches `QUEUED` jobs to Nomad.
- A Nomad event stream listener with a stored stream cursor.
- SSE-based job log streaming through the backend/frontend.

The architecture should evolve from those pieces toward:

```text
DB-backed state machine
+ immutable event log
+ event-driven side effects
+ Nomad execution
+ MinIO data plane
+ quota ledger
+ Nomad stream ingestion
+ periodic reconciliation
```

## Non-Negotiable Invariants

- Postgres owns user-visible product truth.
- Domain events are append-only facts. Never update old facts to explain new behavior.
- Side effects happen only after durable intent exists.
- No run may execute without active prepaid quota reservation.
- A run is not successful until Nomad reports process success, a final MinIO manifest exists, and artifact metadata is projected.
- Reconciliation must be able to repair missed stream events and stale projections by appending correction events.

## Recommended Implementation Order

1. [x] [State Machine, Domain Events, and Outbox Foundation](01-state-machine-events-outbox.md)
   - Add the durable event log, outbox, aggregate sequence rules, consumer dedupe, and projection update contract.

2. [x] [Idempotent User-Intent API](02-idempotent-job-intent-api.md)
   - Generalize idempotency from job submission into a reusable mutating-request contract for submit, cancel, retry, schedule, admin quota, and artifact finalization flows.

3. [Jobs, Runs, Attempts, and State Projections](03-job-run-state-model.md)
   - Evolve the current single `jobs` table into explicit user intent, execution attempt, and current-state projections.

4. [Quota Grants, Reservations, and Usage Ledger](04-quota-grants-reservations-ledger.md)
   - Extend the existing quota window and ledger model into grants, normalized resource multipliers, explicit reservations, and append-only usage/correction entries.

5. [Admission Worker and Transactional Quota Reservation](05-admission-and-fair-queue.md)
   - Move admission from direct submit-time queueing into an event-driven worker that locks quota state, reserves budget, and queues only admitted runs.

6. [Nomad Dispatcher and Runner Contract](06-nomad-dispatcher-and-runner-contract.md)
   - Make dispatch an outbox-driven side effect, define idempotent Nomad job naming, and pass only run metadata plus MinIO references to the runner.

7. [MinIO Specs, Artifacts, and Final Manifests](07-minio-artifacts-and-manifests.md)
   - Promote object storage from single output ZIP URL to structured per-run prefixes, job specs, final manifests, and artifact metadata projections.

8. [Nomad Stream Ingestion](08-nomad-stream-ingestion.md)
   - Refactor the existing stream listener so Nomad events append domain events first, then update projections and realtime notifications.

9. [Realtime Status and Logs Gateway](09-realtime-status-and-logs.md)
   - Build the live UX around projected run state, event notifications, and Nomad log streaming, with clear terminal and logs-unavailable states.

10. [Quota Finalization on Run Completion](10-quota-finalization.md)
    - Consume actual measured runtime, release unused reservation, handle failure/cancel policies, and keep quota read models rebuildable.

11. [Periodic Reconciler](11-reconciler.md)
    - Add repair workers for stuck runs, stale reservations, orphan Nomad allocations, missing manifests, and projection mismatches.

12. [Observability, Auditing, and Operational Controls](12-observability-auditing-ops.md)
    - Add correlation IDs, metrics, dashboards, alerts, admin audit events, and operator-visible repair outcomes.

13. [Event Bus Scaling and Final Cleanup](13-event-bus-scaling-cleanup.md)
    - Keep DB-backed workers for MVP, then introduce Kafka/NATS only when consumer count and throughput justify it; remove legacy wallet/credit paths after migration.

## Execution Guidance

- Implement one step at a time. Each step has concrete migrations, backend work, frontend work, tests, and acceptance criteria.
- Use Flyway migrations for every schema change.
- Keep old behavior available until the replacement is functionally complete and projections can be rebuilt.
- Add compatibility adapters where needed instead of mixing old wallet/credit concepts into new quota/run code.
- Do not optimize with Kafka/NATS before the DB-backed outbox and workers are correct.

## Current-State Gap Summary

| Area | Current state | Target direction |
| --- | --- | --- |
| Events | `domain_events`, `outbox`, aggregate sequences, and consumer dedupe foundation exist; legacy flows do not emit events yet. | Migrate submission, quota, dispatch, Nomad stream, artifacts, and reconciliation onto the event/outbox path. |
| Job model | `jobs` stores both intent and execution state. | Split into `jobs`, `runs`, `run_attempts`, and current projections. |
| Idempotency | Reusable `idempotency_records` exists and `job.submit` uses it; legacy submission columns remain on `jobs` for compatibility. | Extend the shared contract to cancel, retry, admin quota grant, artifact finalization, and later remove submission-specific job columns. |
| Quota | Monthly policy/override/window/ledger exists. | Add grants, explicit reservations, resource multipliers, usage ledger corrections. |
| Dispatch | `FairQueueDispatcherService` polls `QUEUED` jobs and directly calls Nomad. | Worker consumes durable events/outbox and uses idempotent dispatch claims. |
| Nomad stream | Listener updates jobs directly. | Listener appends domain events, then projections consume those events. |
| MinIO | Presigned upload/download for `output.zip`. | Per-run prefixes, spec object, manifests, artifact metadata, manifest verification. |
| Reconciliation | No dedicated reconciler. | Periodic repair workers append correction events and update projections. |
| Realtime | SSE logs exist. | Add state/quota/artifact realtime notifications sourced from event projections. |
