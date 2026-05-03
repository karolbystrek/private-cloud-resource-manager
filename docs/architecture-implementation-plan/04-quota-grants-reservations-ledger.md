# 04. Quota Grants, Reservations, and Usage Ledger

## Goal

Evolve quota from monthly policy/window accounting into a complete ledger-backed quota system:

```text
quota policy
quota grant
quota reservation
quota usage ledger
quota current balance projection
```

The current system is a good start: it has role policies, per-user overrides, quota windows, and append-only ledger
entries. This step makes quota explicit enough to support admin grants, reservations by run, corrections, and
rebuildable
balances.

## Current Repository Baseline

- `quota_policy`: role monthly minutes, role weight, unlimited flag.
- `user_quota_override`: per-user policy override.
- `quota_window`: mutable monthly projection with allocated/reserved/consumed minutes.
- `quota_ledger`: append-only entries for allocation, reserve, consume, refund, adjustment.
- `QuotaAccountingService` locks quota windows for reservation/finalization.

## Database Migration

### `quota_grants`

Add:

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL`
- `interval_start TIMESTAMPTZ NOT NULL`
- `interval_end TIMESTAMPTZ NOT NULL`
- `grant_type VARCHAR(40) NOT NULL`
- `minutes BIGINT NOT NULL`
- `remaining_minutes BIGINT` if you choose mutable projection on grant rows
- `status VARCHAR(40) NOT NULL`
- `source_policy_id UUID`
- `actor_id UUID`
- `reason TEXT`
- `created_at`
- `updated_at`

Events remain audit truth. A mutable `status` or `remaining_minutes` is projection data only.

Grant types:

- `ROLE_GRANT`
- `ADMIN_BONUS`
- `PROMOTIONAL`
- `CORRECTION`
- `MIGRATION`

Grant events:

- `QuotaGrantAdded`
- `QuotaGrantRevoked`
- `QuotaGrantCorrected`
- `QuotaGrantExpired`

### `quota_reservations`

Add:

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL`
- `job_id UUID NOT NULL`
- `run_id UUID NOT NULL`
- `interval_start TIMESTAMPTZ NOT NULL`
- `interval_end TIMESTAMPTZ NOT NULL`
- `reserved_compute_minutes BIGINT NOT NULL`
- `consumed_compute_minutes BIGINT NOT NULL DEFAULT 0`
- `released_compute_minutes BIGINT NOT NULL DEFAULT 0`
- `expires_at TIMESTAMPTZ NOT NULL`
- `status VARCHAR(40) NOT NULL`
- `created_at`
- `updated_at`

Constraints:

- Unique active reservation per run if the policy allows only one max-runtime reservation.
- Check consumed + released <= reserved.

Statuses as projection:

- `ACTIVE`
- `CONSUMED`
- `RELEASED`
- `EXPIRED`
- `CORRECTED`

Reservation events:

- `QuotaReserved`
- `QuotaConsumed`
- `QuotaReleased`
- `QuotaReservationExpired`
- `QuotaReservationCorrected`

### `quota_usage_ledger`

Add a normalized usage ledger or extend `quota_ledger` if you prefer one table.

Recommended fields:

- `id UUID PRIMARY KEY`
- `user_id UUID NOT NULL`
- `job_id UUID`
- `run_id UUID`
- `quota_reservation_id UUID`
- `entry_type VARCHAR(60) NOT NULL`
- `raw_runtime_seconds BIGINT`
- `compute_minutes BIGINT NOT NULL`
- `multiplier INTEGER NOT NULL`
- `reason_code VARCHAR(80)`
- `correlation_id UUID NOT NULL`
- `created_at TIMESTAMPTZ NOT NULL`

Entry types:

- `USAGE_DEBITED`
- `USAGE_RELEASED`
- `USAGE_CORRECTED`
- `ADMIN_ADJUSTMENT`

Do not update previous usage entries. Append corrections.

### `user_quota_balance_current`

Replace or supplement `quota_window`.

Fields:

- `user_id`
- `interval_start`
- `interval_end`
- `granted_minutes`
- `reserved_minutes`
- `consumed_minutes`
- `available_minutes`
- `updated_at`
- `version`

Unique `(user_id, interval_start)`.

## Backend Steps

1. Add entities/repositories for grants, reservations, usage ledger, and current balances.
2. Keep `quota_window` working until the new balance projection is ready.
3. Implement grant generation:
    - on first balance access in an interval
    - or scheduled grant materialization for all active users
    - append `QuotaGrantAdded`
4. Implement admin grant endpoint using idempotency:
    - validate amount/interval/reason
    - append grant row and event
    - update current balance projection
5. Implement reservation service:
    - lock `user_quota_balance_current` row with `SELECT ... FOR UPDATE`
    - calculate normalized compute minutes: `requested_timeout_minutes * multiplier`
    - reject if available is insufficient and user is not unlimited
    - create reservation row
    - append `QuotaReserved`
    - update projection
6. Implement release/consume service:
    - lock same balance row
    - update reservation projection
    - append usage ledger entries and domain events
    - update balance projection

## Migration from Existing Quota Tables

2. Create `quota_grants` from existing `quota_window.allocated_minutes`.
3. Create `user_quota_balance_current` from existing windows:
    - granted = allocated
    - reserved = reserved
    - consumed = consumed
    - available = allocated - reserved - consumed
4. Keep `quota_ledger` entries readable.
5. Start writing new domain events and usage ledger for new runs.
6. Later, decide whether `quota_window` remains as compatibility projection or is removed.

## Tests

- Grant addition increases available balance through projection.
- Grant revoke/correction appends a new event and updates projection without editing old grant events.
- Reservation under concurrent requests cannot overspend.
- Unlimited users still get reservations recorded for audit and fairness.
- Projection can be rebuilt from grants, reservations, and usage ledger.

## Acceptance Criteria

- Quota is explainable through append-only grant/reservation/usage facts.
- Admin additions do not mutate role policy.
- Concurrent admitted runs cannot reserve the same available quota twice.
- The balance projection is rebuildable and can be compared against ledger-derived totals.

