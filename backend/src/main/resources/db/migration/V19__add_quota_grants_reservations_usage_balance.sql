CREATE TABLE quota_grants
(
    id                UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    user_id           UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    interval_start    TIMESTAMP WITH TIME ZONE NOT NULL,
    interval_end      TIMESTAMP WITH TIME ZONE NOT NULL,
    grant_type        VARCHAR(40)              NOT NULL,
    minutes           BIGINT                   NOT NULL,
    remaining_minutes BIGINT                   NOT NULL,
    status            VARCHAR(40)              NOT NULL,
    source_policy_id  UUID REFERENCES quota_policy (id) ON DELETE SET NULL,
    actor_id          UUID REFERENCES users (id) ON DELETE SET NULL,
    reason            TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_quota_grants_dates CHECK (interval_end > interval_start),
    CONSTRAINT check_quota_grants_minutes_non_negative CHECK (minutes >= 0),
    CONSTRAINT check_quota_grants_remaining_non_negative CHECK (remaining_minutes >= 0),
    CONSTRAINT check_quota_grants_remaining_lte_minutes CHECK (remaining_minutes <= minutes)
);

CREATE INDEX idx_quota_grants_user_interval ON quota_grants (user_id, interval_start DESC, interval_end DESC);
CREATE INDEX idx_quota_grants_status ON quota_grants (status);

CREATE TABLE quota_reservations
(
    id                       UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    user_id                  UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id                   UUID                     NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    run_id                   UUID                     NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
    interval_start           TIMESTAMP WITH TIME ZONE NOT NULL,
    interval_end             TIMESTAMP WITH TIME ZONE NOT NULL,
    reserved_compute_minutes BIGINT                   NOT NULL,
    consumed_compute_minutes BIGINT                   NOT NULL DEFAULT 0,
    released_compute_minutes BIGINT                   NOT NULL DEFAULT 0,
    expires_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    status                   VARCHAR(40)              NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_quota_reservations_dates CHECK (interval_end > interval_start),
    CONSTRAINT check_quota_reservations_reserved_non_negative CHECK (reserved_compute_minutes >= 0),
    CONSTRAINT check_quota_reservations_consumed_non_negative CHECK (consumed_compute_minutes >= 0),
    CONSTRAINT check_quota_reservations_released_non_negative CHECK (released_compute_minutes >= 0),
    CONSTRAINT check_quota_reservations_settled_lte_reserved CHECK (
        consumed_compute_minutes + released_compute_minutes <= reserved_compute_minutes
    )
);

CREATE INDEX idx_quota_reservations_user_interval ON quota_reservations (user_id, interval_start DESC);
CREATE INDEX idx_quota_reservations_run_id ON quota_reservations (run_id);
CREATE UNIQUE INDEX uq_quota_reservations_active_run ON quota_reservations (run_id) WHERE status = 'ACTIVE';

CREATE TABLE quota_usage_ledger
(
    id                       UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    user_id                  UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id                   UUID REFERENCES jobs (id) ON DELETE SET NULL,
    run_id                   UUID REFERENCES runs (id) ON DELETE SET NULL,
    quota_reservation_id     UUID REFERENCES quota_reservations (id) ON DELETE SET NULL,
    entry_type               VARCHAR(60)              NOT NULL,
    raw_runtime_seconds      BIGINT,
    compute_minutes          BIGINT                   NOT NULL,
    multiplier               INTEGER                  NOT NULL,
    reason_code              VARCHAR(80),
    correlation_id           UUID                     NOT NULL,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_quota_usage_raw_runtime_non_negative CHECK (
        raw_runtime_seconds IS NULL OR raw_runtime_seconds >= 0
    ),
    CONSTRAINT check_quota_usage_compute_minutes_non_negative CHECK (compute_minutes >= 0),
    CONSTRAINT check_quota_usage_multiplier_positive CHECK (multiplier > 0)
);

CREATE INDEX idx_quota_usage_ledger_user_created_at ON quota_usage_ledger (user_id, created_at DESC);
CREATE INDEX idx_quota_usage_ledger_run_id ON quota_usage_ledger (run_id) WHERE run_id IS NOT NULL;
CREATE INDEX idx_quota_usage_ledger_reservation_id ON quota_usage_ledger (quota_reservation_id) WHERE quota_reservation_id IS NOT NULL;

CREATE TABLE user_quota_balance_current
(
    id                UUID PRIMARY KEY                  DEFAULT uuid_generate_v4(),
    user_id           UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    interval_start    TIMESTAMP WITH TIME ZONE NOT NULL,
    interval_end      TIMESTAMP WITH TIME ZONE NOT NULL,
    granted_minutes   BIGINT                   NOT NULL,
    reserved_minutes  BIGINT                   NOT NULL DEFAULT 0,
    consumed_minutes  BIGINT                   NOT NULL DEFAULT 0,
    available_minutes BIGINT                   NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_quota_balance_user_interval UNIQUE (user_id, interval_start),
    CONSTRAINT check_user_quota_balance_dates CHECK (interval_end > interval_start),
    CONSTRAINT check_user_quota_balance_granted_non_negative CHECK (granted_minutes >= 0),
    CONSTRAINT check_user_quota_balance_reserved_non_negative CHECK (reserved_minutes >= 0),
    CONSTRAINT check_user_quota_balance_consumed_non_negative CHECK (consumed_minutes >= 0),
    CONSTRAINT check_user_quota_balance_available_non_negative CHECK (available_minutes >= 0)
);

CREATE INDEX idx_user_quota_balance_user_id ON user_quota_balance_current (user_id);
CREATE INDEX idx_user_quota_balance_interval ON user_quota_balance_current (interval_start DESC);

ALTER TABLE runs
    ADD CONSTRAINT fk_runs_quota_reservation_id FOREIGN KEY (quota_reservation_id) REFERENCES quota_reservations (id) ON DELETE SET NULL;

INSERT INTO quota_grants (
    user_id,
    interval_start,
    interval_end,
    grant_type,
    minutes,
    remaining_minutes,
    status,
    reason,
    created_at,
    updated_at
)
SELECT user_id,
       window_start,
       window_end,
       'MIGRATION',
       allocated_minutes,
       GREATEST(0, allocated_minutes - reserved_minutes - consumed_minutes),
       'ACTIVE',
       'Migrated from quota_window',
       updated_at,
       updated_at
FROM quota_window
WHERE allocated_minutes > 0;

INSERT INTO user_quota_balance_current (
    user_id,
    interval_start,
    interval_end,
    granted_minutes,
    reserved_minutes,
    consumed_minutes,
    available_minutes,
    updated_at,
    version
)
SELECT user_id,
       window_start,
       window_end,
       allocated_minutes,
       reserved_minutes,
       consumed_minutes,
       GREATEST(0, allocated_minutes - reserved_minutes - consumed_minutes),
       updated_at,
       version
FROM quota_window
ON CONFLICT (user_id, interval_start) DO NOTHING;

WITH inserted_reservations AS (
    INSERT INTO quota_reservations (
        user_id,
        job_id,
        run_id,
        interval_start,
        interval_end,
        reserved_compute_minutes,
        consumed_compute_minutes,
        released_compute_minutes,
        expires_at,
        status,
        created_at,
        updated_at
    )
    SELECT runs.user_id,
           runs.job_id,
           runs.id,
           date_trunc('month', COALESCE(runs.queued_at, runs.created_at)),
           date_trunc('month', COALESCE(runs.queued_at, runs.created_at)) + INTERVAL '1 month',
           runs.current_lease_reserved_minutes,
           0,
           0,
           runs.active_lease_expires_at,
           'ACTIVE',
           COALESCE(runs.queued_at, runs.created_at),
           runs.updated_at
    FROM runs
    WHERE runs.current_lease_reserved_minutes > 0
      AND runs.lease_settled = FALSE
      AND runs.active_lease_expires_at IS NOT NULL
    RETURNING id, run_id
)
UPDATE runs
SET quota_reservation_id = inserted_reservations.id
FROM inserted_reservations
WHERE runs.id = inserted_reservations.run_id;

INSERT INTO quota_usage_ledger (
    user_id,
    job_id,
    run_id,
    quota_reservation_id,
    entry_type,
    raw_runtime_seconds,
    compute_minutes,
    multiplier,
    reason_code,
    correlation_id,
    created_at
)
SELECT quota_ledger.user_id,
       quota_ledger.job_id,
       quota_ledger.run_id,
       runs.quota_reservation_id,
       CASE quota_ledger.entry_type
           WHEN 'LEASE_CONSUME' THEN 'USAGE_DEBITED'
           WHEN 'LEASE_REFUND' THEN 'USAGE_RELEASED'
           WHEN 'ADMIN_ADJUSTMENT' THEN 'ADMIN_ADJUSTMENT'
           ELSE 'USAGE_CORRECTED'
           END,
       NULL,
       quota_ledger.minutes,
       1,
       LEFT(quota_ledger.reason, 80),
       uuid_generate_v4(),
       quota_ledger.created_at
FROM quota_ledger
LEFT JOIN runs ON runs.id = quota_ledger.run_id
WHERE quota_ledger.entry_type IN ('LEASE_CONSUME', 'LEASE_REFUND', 'ADMIN_ADJUSTMENT');

DROP TABLE quota_ledger;
DROP TABLE quota_window;
