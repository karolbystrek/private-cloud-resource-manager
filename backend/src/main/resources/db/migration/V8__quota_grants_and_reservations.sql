CREATE TABLE quota_grants
(
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id         UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    interval_start     TIMESTAMPTZ  NOT NULL,
    interval_end       TIMESTAMPTZ  NOT NULL,
    grant_type         VARCHAR(40)  NOT NULL,
    minutes            BIGINT       NOT NULL,
    remaining_minutes  BIGINT       NOT NULL,
    status             VARCHAR(40)  NOT NULL,
    source_policy_id   UUID REFERENCES quota_policy (id) ON DELETE SET NULL,
    actor_id           UUID REFERENCES profiles (id) ON DELETE SET NULL,
    reason             TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_quota_grants_interval CHECK (interval_end > interval_start),
    CONSTRAINT chk_quota_grants_minutes_nn CHECK (minutes >= 0),
    CONSTRAINT chk_quota_grants_remaining_nn CHECK (remaining_minutes >= 0),
    CONSTRAINT chk_quota_grants_remaining_lte_minutes CHECK (remaining_minutes <= minutes)
);

CREATE INDEX idx_quota_grants_profile_interval ON quota_grants (profile_id, interval_start DESC, interval_end DESC);
CREATE INDEX idx_quota_grants_status ON quota_grants (status);

CREATE TABLE quota_reservations
(
    id                           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id                   UUID NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    job_id                       UUID NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    run_id                       UUID NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
    interval_start               TIMESTAMPTZ NOT NULL,
    interval_end                 TIMESTAMPTZ NOT NULL,
    reserved_compute_minutes     BIGINT NOT NULL,
    consumed_compute_minutes     BIGINT NOT NULL DEFAULT 0,
    released_compute_minutes     BIGINT NOT NULL DEFAULT 0,
    expires_at                   TIMESTAMPTZ NOT NULL,
    status                       VARCHAR(40) NOT NULL,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_quota_reservations_interval CHECK (interval_end > interval_start),
    CONSTRAINT chk_quota_reservations_reserved_nn CHECK (reserved_compute_minutes >= 0),
    CONSTRAINT chk_quota_reservations_consumed_nn CHECK (consumed_compute_minutes >= 0),
    CONSTRAINT chk_quota_reservations_released_nn CHECK (released_compute_minutes >= 0),
    CONSTRAINT chk_quota_reservations_settled CHECK (
            consumed_compute_minutes + released_compute_minutes <= reserved_compute_minutes
        )
);

CREATE INDEX idx_quota_reservations_profile_interval ON quota_reservations (profile_id, interval_start DESC);
CREATE INDEX idx_quota_reservations_run_id ON quota_reservations (run_id);

CREATE UNIQUE INDEX uq_quota_reservations_active_run ON quota_reservations (run_id)
    WHERE status = 'ACTIVE';

ALTER TABLE runs
    ADD CONSTRAINT fk_runs_quota_reservation_id FOREIGN KEY (quota_reservation_id) REFERENCES quota_reservations (id) ON DELETE SET NULL;
