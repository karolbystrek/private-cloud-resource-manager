### Job Stdout/Stderr Live Logs via SSE (Backend Proxy to Nomad)

#### Summary
- Add live job log streaming in the UI using SSE, with backend as the only Nomad-facing proxy.
- Use Nomad’s documented log APIs:  
  [Client API: Stream Logs (`/v1/client/fs/logs/:alloc_id`)](https://developer.hashicorp.com/nomad/api-docs/client#stream-logs) and  
  [Jobs API: List Job Allocations (`/v1/job/:job_id/allocations`)](https://developer.hashicorp.com/nomad/api-docs/jobs#list-job-allocations).
- Explicitly handle transient log retention (logs may be missing after completion), showing a clear “logs unavailable” state plus artifact download path.
- Scope excludes long-term container log storage.

#### Public API / Interface Changes
- Add backend SSE endpoint:
  `GET /api/jobs/{jobId}/logs/stream?stream=stdout|stderr&offset=<int>`
- SSE event contract (JSON payloads):
  `meta`, `chunk`, `status`, `unavailable`, `end`, `heartbeat`
- Add frontend proxy route for EventSource compatibility with current auth model:
  `GET /api/jobs/[id]/logs/stream?...`
- UI supports stream toggle (`stdout` + `stderr`), starts from beginning (offset 0), and reconnects with last seen offset.

#### Implementation Changes
- Backend:
  - Add a dedicated log-stream service that:
    - Authorizes access using existing job ownership/admin rules.
    - Resolves Nomad job id from current template convention: `user#{ownerId}-job#{jobId}`.
    - Resolves allocation via `/v1/job/:job_id/allocations?all=true`, selecting most relevant allocation deterministically.
    - Calls Nomad logs endpoint `/v1/client/fs/logs/:alloc_id` with:
      `task=user-workload`, `type=<stdout|stderr>`, `follow=<based on job state>`, `origin=start`, `offset=<client offset>`, `plain=false`.
    - Translates Nomad log frames to SSE events and propagates offsets for resumable reconnects.
  - Add clear error mapping:
    - `404` from Nomad logs for terminal jobs => `unavailable` (retention expired / disabled / removed).
    - missing allocation for non-terminal jobs => retryable `status`.
- Frontend:
  - Add logs panel in job details with:
    - stdout/stderr toggle (selected by user).
    - bounded ring buffer (trim oldest lines to keep UI responsive).
    - auto-reconnect with exponential backoff and offset resume.
    - explicit connection state badges/messages.
    - terminal “logs unavailable” UX with artifact download CTA.
  - Keep existing artifact flow as fallback for completed jobs.

#### Test Plan
- Backend unit tests:
  - allocation selection logic across multiple allocations/statuses.
  - Nomad frame decode to SSE events and offset propagation.
  - 404/missing-allocation/error translation behavior.
- Backend integration/resource tests:
  - owner access allowed, non-owner denied, admin allowed.
  - SSE response headers and event format validation.
- Frontend tests:
  - EventSource lifecycle (connect, chunk append, reconnect, unavailable handling).
  - ring-buffer trimming behavior.
  - stdout/stderr toggle stream reset behavior.
- Manual scenario checks:
  - active running log stream.
  - finished job with available logs.
  - finished job with expired logs showing degraded state.

#### Assumptions and Defaults
- Selected choices:
  - stream scope: `stdout + stderr toggle`
  - initial view: `from start`
  - post-finish missing logs: show `logs unavailable` + artifact link
  - reconnect: auto-reconnect with backoff
  - client buffering: bounded ring buffer
- No DB migration required for this feature.
- Long-term log retention/storage is intentionally out of scope.
- Nomad log volatility is expected (rotation/ephemeral retention per docs):  
  [logs block behavior](https://developer.hashicorp.com/nomad/docs/job-specification/logs).
