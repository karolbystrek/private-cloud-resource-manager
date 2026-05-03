# 01. State Machine, Domain Events, and Outbox Foundation

## Goal

Create the durable backbone for the target architecture:

```text
transactional state change
-> immutable domain event
-> outbox row
-> worker side effect
-> projection update
```

This step should be completed before large lifecycle refactors. It gives every later step a consistent way to record
facts, publish work, and rebuild read models.

## Current Repository Baseline

- There is no general `domain_events` table.
- There is no `outbox` table.
- `quota_ledger` is append-only for quota entries, but it is quota-specific.
- `NomadEventStreamListener` currently mutates `jobs` directly.
- Job submission writes `jobs` directly and returns the created job.

## Database Migrations

Add a Flyway migration for core event infrastructure.

### `domain_events`

Fields:

- `id UUID PRIMARY KEY`
- `event_type VARCHAR(120) NOT NULL`
- `aggregate_type VARCHAR(80) NOT NULL`
- `aggregate_id VARCHAR(255) NOT NULL`
- `sequence_number BIGINT NOT NULL`
- `occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `schema_version INTEGER NOT NULL DEFAULT 1`
- `actor_type VARCHAR(40)`
- `actor_id VARCHAR(255)`
- `user_id UUID`
- `job_id UUID`
- `causation_id UUID`
- `correlation_id UUID NOT NULL`
- `idempotency_key VARCHAR(128)`
- `source VARCHAR(80) NOT NULL`
- `metadata JSONB NOT NULL DEFAULT '{}'::jsonb`
- `payload JSONB NOT NULL`

Constraints and indexes:

- Unique `(aggregate_type, aggregate_id, sequence_number)`.
- Index `(aggregate_type, aggregate_id, sequence_number)`.
- Index `(correlation_id)`.
- Index `(event_type, occurred_at DESC)`.
- Index `(user_id, occurred_at DESC)` where `user_id IS NOT NULL`.
- Index `(job_id, occurred_at DESC)` where `job_id IS NOT NULL`.

Do not add foreign keys from `domain_events.user_id` or `domain_events.job_id` to mutable domain tables. Events are
historical facts and must not lose identity context when a user or job row is deleted.

### `outbox`

Fields:

- `id UUID PRIMARY KEY`
- `event_id UUID NOT NULL REFERENCES domain_events(id)`
- `topic VARCHAR(160) NOT NULL`
- `payload JSONB NOT NULL`
- `headers JSONB NOT NULL DEFAULT '{}'::jsonb`
- `available_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `published_at TIMESTAMPTZ`
- `claimed_at TIMESTAMPTZ`
- `claimed_by VARCHAR(120)`
- `attempt_count INTEGER NOT NULL DEFAULT 0`
- `last_error TEXT`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`

Constraints and indexes:

- Unique `(event_id, topic)`.
- Partial ready-to-publish index: `(available_at, created_at, id)` where
  `published_at IS NULL AND claimed_at IS NULL`.
- Index for stuck claims: `(claimed_at)` where `published_at IS NULL AND claimed_at IS NOT NULL`.

Keep `outbox.payload` as the handler/integration payload. Use `outbox.headers` for event id, correlation id,
causation id, schema version, content type, source, and partition key. Broker-specific message ids or offsets are
deferred until an external broker publisher exists.

### `event_consumer_dedupe`

Fields:

- `consumer_name VARCHAR(120) NOT NULL`
- `source VARCHAR(120) NOT NULL`
- `event_id UUID NOT NULL`
- `processed_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `PRIMARY KEY (consumer_name, source, event_id)`

Use this table for every projection or worker that consumes domain events.
Include `source` so the same consumer can safely dedupe both local `domain_events` and future events from external
services without requiring a local foreign key.

### Aggregate Sequence Locking

Add either:

- `aggregate_sequences(aggregate_type, aggregate_id, next_sequence_number, updated_at)`, locked with
  `SELECT ... FOR UPDATE`; or
- a repository query that calculates and locks per-aggregate sequence through a compact sequence table.

Do not calculate sequence numbers by `MAX(sequence_number) + 1` without a lock.

## Backend Implementation

1. Create `events` package:
    - `DomainEvent`
    - `OutboxMessage`
    - `EventConsumerDedupe`
    - repositories for all three tables
    - `DomainEventAppender`
    - `OutboxWriter`
    - `AggregateSequenceService`

2. Implement `DomainEventAppender.append(...)`:
    - Require caller to pass aggregate type/id, event type, payload object, source, actor,
      causation/correlation/idempotency metadata.
    - Allocate aggregate sequence inside the same transaction.
    - Serialize payload with Jackson into JSONB.
    - Default `schema_version` to `1` and `metadata` to `{}`.
    - Insert into `domain_events`.
    - Insert one or more `outbox` rows for topics derived from event type.

3. Define event topic mapping:
    - `job.submitted`
    - `run.queued`
    - `run.dispatch.requested`
    - `run.dispatched`
    - `run.started`
    - `run.finished`
    - `run.finalized`
    - `quota.reserved`
    - `quota.consumed`
    - `quota.released`
    - `artifact.finalized`
    - `nomad.signal.received`

4. Add an `OutboxPoller`:
    - Poll unpublished rows with `FOR UPDATE SKIP LOCKED`.
    - Mark `claimed_at`, `claimed_by`, increment `attempt_count`.
    - Dispatch to in-process handlers for MVP.
    - Mark `published_at` after handler success.
    - Store `last_error` and back off after failure.

5. Add worker claim configuration:
    - batch size
    - claim timeout
    - retry delay
    - worker instance name

6. Add consumer dedupe helper:
    - Insert `(consumer_name, source, event_id)` at the start of a handler transaction.
    - If unique violation happens, skip work.

## Retention Policy

- Keep `domain_events` long term as the durable audit and replay source.
- Treat `outbox` as transient queue state; published rows may be deleted or archived after 30 days once cleanup exists.
- Keep `event_consumer_dedupe` rows for at least 90 days, or longer than the longest broker redelivery window once an
  external broker is introduced.

## Refactoring Rules

- Do not convert every existing path in this step.
- Start by making new code capable of appending events and outbox messages.
- After the foundation exists, later steps migrate submission, quota, dispatch, Nomad stream, artifacts, and
  reconciliation to it.

## Acceptance Criteria

- A single backend service can append a domain event and matching outbox row transactionally.
- Two concurrent appends for the same aggregate produce sequence numbers `N` and `N+1`, never duplicates.
- Outbox workers can safely claim and process messages idempotently.
- No side effect worker needs to run inside the original API request transaction.
