# 08. Nomad Stream Ingestion

## Goal

Refactor the existing Nomad stream listener so infrastructure signals become immutable domain events first, then projection updates.

Nomad stream provides fast signals. It is not the product source of truth.

## Current Repository Baseline

- `NomadEventStreamListener` connects to `/v1/event/stream`.
- Cursor is stored in `nomad_event_stream_cursor`.
- Listener updates nodes and jobs directly.
- Terminal allocation events settle quota directly.

## Target Flow

```text
Nomad event stream line
-> normalize infrastructure signal
-> append domain event
-> update projection idempotently
-> emit realtime notification
```

## Event Normalization

Map Nomad events into domain events:

- allocation pending -> `RunPendingResources`
- allocation running -> `RunStarted`
- task started -> `RunTaskStarted`
- task exit 0 -> `RunProcessSucceeded`
- task exit nonzero -> `RunProcessFailed`
- OOM -> `RunProcessFailed` with reason `OOM_KILLED`
- allocation lost -> `RunInfraFailed`
- job deregistered by cancellation -> `RunCanceled`
- node drain/ineligible -> `NodeSchedulingStateChanged`

Keep raw Nomad payload reference or compact raw JSON in event payload when useful for audit.

## Database Changes

Add `nomad_event_ingestion_dedupe`:

- `nomad_index BIGINT NOT NULL`
- `topic VARCHAR(80) NOT NULL`
- `event_type VARCHAR(120) NOT NULL`
- `dedupe_key VARCHAR(240) NOT NULL`
- `processed_at TIMESTAMPTZ NOT NULL`
- primary key `(nomad_index, dedupe_key)`

Dedupe key examples:

- allocation ID + client status + modify index
- task name + task event time/type
- node ID + status + modify index

Extend `nomad_event_stream_cursor` if needed:

- `topic_filter`
- `updated_at`
- `status`

## Backend Steps

1. Split current listener into:
   - `NomadStreamClient`
   - `NomadEventNormalizer`
   - `NomadSignalIngestionService`
   - projection handlers
2. Keep cursor update in the same transaction as processed events.
3. Resolve run by:
   - Nomad job ID meta
   - allocation meta
   - deterministic Nomad job ID
4. Append domain events, do not directly mutate run state in the stream client.
5. Projection handlers consume the new domain events:
   - update `runs_current`
   - update Nomad mappings
   - update node projections
   - trigger quota runtime marker events
6. On unknown run/allocation:
   - append `NomadSignalUnmatched` or store unmatched signal for reconciler
   - do not invent product state
7. Track reconnects and lag metrics.

## Ordering Rules

- Use per-run aggregate sequence for run events.
- Nomad events can arrive duplicate or out of order.
- Projection handlers must ignore stale transitions:
   - terminal states should not regress to running
   - `RunStarted` after terminal should become an ignored/stale signal event or reconciliation note
- Always append a correction event if state needs repair.

## Quota Interaction

Stream ingestion should record runtime facts:

- `RunStarted` with trusted start timestamp
- `RunProcessSucceeded` or `RunProcessFailed` with finish timestamp

Quota finalization should be a separate consumer of terminal run events, not hardcoded into the stream listener.

## Tests

- Duplicate Nomad signal does not duplicate domain event effects.
- Allocation running appends `RunStarted` and updates projection.
- OOM signal maps to explicit failure reason.
- Terminal signal does not mark run `SUCCEEDED` until artifact manifest step finishes.
- Cursor is not advanced if event processing transaction fails.
- Unknown allocation is retained for later reconciliation.

## Acceptance Criteria

- Nomad stream listener no longer mutates job/run/quota state directly.
- Every useful infrastructure signal is represented as an immutable event.
- Duplicate and out-of-order Nomad stream messages are safe.
- Reconciler can repair any missed stream event.

