CREATE TABLE quota_usage_ledger
(
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id            UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    job_id                UUID REFERENCES jobs (id) ON DELETE SET NULL,
    run_id                UUID REFERENCES runs (id) ON DELETE SET NULL,
    quota_reservation_id  UUID REFERENCES quota_reservations (id) ON DELETE SET NULL,
    entry_type            VARCHAR(60) NOT NULL,
    raw_runtime_seconds   BIGINT,
    compute_minutes       BIGINT      NOT NULL,
    multiplier            INTEGER     NOT NULL,
    reason_code           VARCHAR(80),
    correlation_id        UUID        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_quota_usage_raw_seconds_nn CHECK (
            raw_runtime_seconds IS NULL OR raw_runtime_seconds >= 0
        ),
    CONSTRAINT chk_quota_usage_compute_nn CHECK (compute_minutes >= 0),
    CONSTRAINT chk_quota_usage_multiplier_pos CHECK (multiplier > 0)
);

CREATE INDEX idx_quota_usage_ledger_profile_created_at ON quota_usage_ledger (profile_id, created_at DESC);
CREATE INDEX idx_quota_usage_ledger_run_id ON quota_usage_ledger (run_id) WHERE run_id IS NOT NULL;
CREATE INDEX idx_quota_usage_ledger_reservation_id ON quota_usage_ledger (quota_reservation_id)
    WHERE quota_reservation_id IS NOT NULL;

CREATE TABLE user_quota_balance_current
(
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id         UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    interval_start     TIMESTAMPTZ NOT NULL,
    interval_end       TIMESTAMPTZ NOT NULL,
    granted_minutes    BIGINT      NOT NULL,
    reserved_minutes   BIGINT      NOT NULL DEFAULT 0,
    consumed_minutes   BIGINT      NOT NULL DEFAULT 0,
    available_minutes  BIGINT      NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_quota_balance_profile_interval UNIQUE (profile_id, interval_start),
    CONSTRAINT chk_user_quota_balance_dates CHECK (interval_end > interval_start),
    CONSTRAINT chk_user_quota_balance_granted_nn CHECK (granted_minutes >= 0),
    CONSTRAINT chk_user_quota_balance_reserved_nn CHECK (reserved_minutes >= 0),
    CONSTRAINT chk_user_quota_balance_consumed_nn CHECK (consumed_minutes >= 0),
    CONSTRAINT chk_user_quota_balance_available_nn CHECK (available_minutes >= 0)
);

CREATE INDEX idx_user_quota_balance_profile_id ON user_quota_balance_current (profile_id);
CREATE INDEX idx_user_quota_balance_interval ON user_quota_balance_current (interval_start DESC);
