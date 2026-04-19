# Quota-First Billing + Fair Scheduler (Production Plan)

## Summary

- Replace `credits-per-resource` model with `time-quota` model: charge only wall-clock lease time, 15-minute chunks.
- Keep hard invariant: no job run without active prepaid lease chunk. Renewal fail or quota exhausted => no renew => kill at lease expiry.
- Window model: calendar month, no carryover.
- Policy model: role default + per-user override.
- Defaults locked: `STUDENT 20h/month`, `EMPLOYEE 80h/month`, `ADMIN unlimited`; fairness weights `1/2/4`.
- Wallet/credit system fully replaced.
- Time/accounting representation: use integer minutes everywhere (`BIGINT`), never floats.

## Implementation Changes

### Data model (Flyway)

- Add `quota_policy(role, monthly_minutes, role_weight, unlimited, active_from)`.
- Add `user_quota_override(user_id, monthly_minutes, role_weight, unlimited, active_from, expires_at)`.
- Add `quota_window(user_id, window_start, window_end, allocated_minutes, reserved_minutes, consumed_minutes, updated_at, version)` with unique `(user_id, window_start)`.
- Add append-only `quota_ledger(user_id, job_id, lease_seq, entry_type, minutes, reason, created_at)`; entry types: `WINDOW_ALLOCATION`, `LEASE_RESERVE`, `LEASE_CONSUME`, `LEASE_REFUND`, `ADMIN_ADJUSTMENT`.
- Add `jobs.total_consumed_minutes` (replace semantic of `totalCostCredits`).
- Add `jobs.status=QUEUED` for broker-controlled admission queue.
- Store all quota and lease quantities as integer minutes (`BIGINT`); no decimal numeric columns for billing/quota.

### Admission + lease flow (backend)

- Job submit path no longer computes CPU/RAM price. It reserves first 15 min quota atomically (`SELECT ... FOR UPDATE` on `quota_window` row), writes `LEASE_RESERVE`, creates `QUEUED` job.
- Dispatcher service picks queued jobs by fairness score, dispatches to Nomad.
- Lease renewal path reserves next 15 min with same lock/ledger rules.
- If reserve fails on renewal (quota empty / transient failure), return non-200; sidecar kills at current lease expiry (existing hard-stop behavior stays).
- Job end/fail path computes unused part of current prepaid chunk in integer minutes, writes `LEASE_REFUND`, releases reserved minutes.

### Fair scheduling (resource fairness + anti-starvation)

- Use role-weighted deficit + aging.
- Per-user dynamic quantum each scheduling cycle:
  - `quantum = base * role_weight * (1 + (1 - usage_ratio))`
  - `usage_ratio = consumed_minutes / allocated_minutes` (clamped `0..1`; unlimited users use `ratio=1`).
- Job cost in deficit uses dominant resource share (not billing, only fairness):
  - `cost = max(req_cpu/cluster_cpu, req_ram/cluster_ram, req_gpu/cluster_gpu) * 100`.
- Select runnable job with highest `deficit - cost + age_boost`; tie-break oldest enqueue.
- Prevent starvation with monotonic age boost; any eligible queued user eventually outranks heavy repeat users.

### API/contracts

- Replace wallet endpoints with quota endpoints:
  - `GET /api/quota/me` => current window allocation/consumed/reserved/remaining, role policy, reset timestamp.
  - `GET /api/quota/ledger?window=...`
  - Admin: `PUT /api/admin/quota/policies/{role}`, `PUT /api/admin/quota/overrides/{userId}`.
- `POST /api/jobs` errors:
  - `402` -> insufficient remaining quota for initial lease.
  - `429` -> accepted but delayed due fairness queue pressure (optional if async accepted response used).
- Job DTO rename `totalCostCredits` -> `totalConsumedMinutes` (frontend + backend DTO parity).
- API time/quota fields represented as integer minutes only.

### Migration/rollout

- Release 1: add quota schema + services + metrics behind feature flag `billing.mode=credits|quota`.
- Release 2: switch submit/renew/refund to quota mode; frontend shows quota dashboard.
- Release 3: remove wallet/credit paths and tables after retention window, with one-time archival export.

## Public Interface / Type Changes

- Backend DTOs:
  - `JobDetailsResponse.totalCostCredits` -> `totalConsumedMinutes`.
  - New `QuotaSummaryResponse`, `QuotaLedgerEntryResponse`, `QuotaPolicyResponse`.
- Frontend types (jobs + dashboard):
  - Replace `totalCostCredits: number` with `totalConsumedMinutes: number`.
  - Add quota state model: allocated/consumed/reserved/remaining/resetAt.
- Error contract:
  - Keep ProblemDetail shape; update detail messages from “Insufficient balance” to “Insufficient quota”.

## Assumptions (Locked Defaults)

- Billing dimension = wall-clock minutes only, independent of requested resources.
- Lease chunk stays 15 min.
- Window timezone for quota reset = UTC (recommended for ops consistency).
- ADMIN unlimited quota still passes fairness queue (no bypass of scheduler fairness).
- Wallet/credits removed after migration completion.
