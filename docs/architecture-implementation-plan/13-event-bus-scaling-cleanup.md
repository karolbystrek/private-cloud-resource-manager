# 13. Event Bus Scaling and Final Cleanup

## Goal

Keep the MVP simple with DB-backed workers, then introduce Kafka/NATS only when the outbox has multiple independent consumers or throughput needs exceed safe database polling.

Also remove legacy wallet/credit paths after the quota/run/event architecture is complete.

## MVP Rule

Do not add Kafka/NATS until:

- domain events and outbox are in production use
- workers are idempotent
- consumer dedupe exists
- projection rebuilds work
- outbox lag/throughput is measured

The event bus propagates facts. It must never become truth.

## DB-Backed Worker Maturity Checklist

Before external bus:

- Outbox rows are claimed with `FOR UPDATE SKIP LOCKED`.
- Failed messages retry with backoff.
- Poison messages are visible to operators.
- Consumers are idempotent with `event_consumer_dedupe`.
- Metrics expose lag, attempts, and failures.
- Workers can be horizontally scaled.

## Kafka/NATS Introduction Plan

1. Keep `domain_events` and `outbox` unchanged.
2. Add publisher process that reads outbox and publishes to broker topics.
3. Mark outbox rows published only after broker ack.
4. Consumers still dedupe by `event_id`.
5. Keep DB-backed fallback consumers for critical workflows until broker reliability is proven.
6. Do not let consumers mutate domain event payloads.

Recommended topic families:

- `job.events`
- `run.events`
- `quota.events`
- `artifact.events`
- `nomad.signals`
- `admin.audit`

## Legacy Cleanup

### Wallet/Credit System

Current repo still contains wallet and credit registry packages.

Cleanup after quota migration:

1. Confirm all billing reads use quota endpoints.
2. Export or archive wallet/credit history if retention is required.
3. Remove wallet API from frontend/backend.
4. Remove wallet service/repository/entities if no longer referenced.
5. Keep old tables read-only for retention or migrate into historical ledger.
6. Drop old tables only after explicit retention decision.

### Old Job Fields

After runs are fully adopted:

- remove execution state from `jobs`
- remove submission-specific idempotency columns from `jobs`
- remove old artifact URL field
- remove single-job lease fields

Only do this after compatibility endpoints have been migrated.

### Old Direct Mutation Paths

Search for code paths that:

- update run/job state without appending domain events
- dispatch Nomad directly from API request handling
- settle quota outside quota finalization service
- mark success without manifest

Replace them with event-driven services or documented projection handlers.

## Security Hardening Checklist

Complete before production:

- runner containers run as non-root
- no Docker socket mounted
- resource limits enforced by Nomad/Docker
- network egress restricted by default
- short-lived MinIO credentials or presigned URLs
- per-run object prefixes
- secrets injected by reference, not stored in job specs unless encrypted
- artifact size/type limits
- workspace cleanup
- admin quota mutations audited
- internal runner endpoints authenticated

## Tests

- External bus publisher does not lose outbox messages on publish failure.
- Consumers tolerate duplicate broker messages.
- Removing legacy fields does not break job list/details/download.
- No backend service dispatches compute without a durable run and quota reservation.

## Acceptance Criteria

- DB-backed architecture works independently of Kafka/NATS.
- Kafka/NATS can be introduced as propagation infrastructure without changing product truth.
- Legacy wallet/credit/job-state paths are removed or clearly marked read-only.
- Security hardening items are tracked and either implemented or explicitly deferred with risk.

