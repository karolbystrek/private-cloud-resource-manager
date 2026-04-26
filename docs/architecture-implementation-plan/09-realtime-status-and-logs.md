# 09. Realtime Status and Logs Gateway

## Goal

Provide live user feedback from reliable state projections and transient infrastructure streams.

Realtime is a UX layer. It must not own orchestration, quota, or product truth.

## Current Repository Baseline

- Backend exposes `/jobs/{id}/logs/stream` with SSE.
- Frontend has a job logs panel that consumes the stream.
- There is a separate docs plan for job logs SSE.
- Job status is fetched through job details/list endpoints.

## Target Realtime Channels

Per run:

- run state changes
- queue/admission changes
- dispatch state
- runtime markers
- log chunks
- artifact finalization

Per user:

- quota balance changes
- job/run submitted or terminal notifications

Admin/operator:

- queue depth
- stale reservations
- reconciler corrections
- outbox lag

## Backend Steps

1. Keep the current SSE log endpoint as the first realtime transport.
2. Add event notification service:
   - consumes domain events or projection changes
   - emits SSE events to subscribed clients
3. Add run status stream endpoint:
   - `GET /runs/{runId}/events/stream`
   - or compatibility `GET /jobs/{jobId}/events/stream` resolving current run
4. Emit typed events:
   - `run.status`
   - `run.queue`
   - `quota.balance`
   - `artifact.finalized`
   - `log.chunk`
   - `heartbeat`
   - `end`
5. Use projection snapshots on connect:
   - send current run state first
   - then stream new updates
6. Add authorization:
   - user can subscribe only to own job/run/user channels
   - admin can subscribe to operational channels
7. Add backpressure and disconnect handling.

## Log Streaming Rules

Live logs may come from Nomad allocation logs.

Rules:

- Missing allocation for non-terminal run is retryable.
- Missing logs for terminal run may be non-retryable.
- If logs are no longer retained, UI must show clear logs-unavailable state.
- Artifact archive is the durable fallback if archived logs exist.

## Frontend Steps

1. Keep job details polling as fallback.
2. Add event stream hook for run state:
   - handles reconnect
   - dedupes by event ID or sequence
   - updates local state from projection event payload
3. Update job details page:
   - show `SUBMITTED`, `QUOTA_CHECKING`, `QUEUED`, `DISPATCHING`, `RUNNING`, `FINALIZING`, terminal states
   - show queue position when available
   - show quota rejection reason when failed before dispatch
4. Update logs panel:
   - distinguish waiting for allocation, streaming, retryable unavailable, terminal unavailable
   - keep download action visible when artifacts finalized
5. Add quota balance updates to dashboard/header if already displayed.

## Event Payload Shape

Common fields:

- `eventId`
- `eventType`
- `aggregateType`
- `aggregateId`
- `sequenceNumber`
- `occurredAt`
- `correlationId`
- `payload`

The frontend should not parse raw Nomad payloads. Backend projections should provide UI-ready state.

## Tests

Backend:

- Unauthorized users cannot subscribe to another user run.
- First SSE event contains current state snapshot.
- Duplicate domain event does not emit duplicate state transition.
- Disconnected clients are cleaned up.

Frontend:

- State changes update the visible lifecycle.
- Logs unavailable terminal state is distinct from loading.
- Artifact link appears after finalization event.
- Reconnect does not duplicate log lines where offsets are provided.

## Acceptance Criteria

- Users see run lifecycle changes without manual refresh.
- Live logs remain best-effort and clearly communicate unavailable states.
- Realtime events are derived from backend truth, not browser-side orchestration assumptions.

