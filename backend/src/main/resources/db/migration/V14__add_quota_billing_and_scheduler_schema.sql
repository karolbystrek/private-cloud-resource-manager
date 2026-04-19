CREATE TABLE quota_policy
(
    id              UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    role            VARCHAR(20)         NOT NULL,
    monthly_minutes BIGINT              NOT NULL DEFAULT 0,
    role_weight     INTEGER             NOT NULL DEFAULT 1,
    unlimited       BOOLEAN             NOT NULL DEFAULT FALSE,
    active_from     TIMESTAMP WITH TIME ZONE    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quota_policy_role_active_from UNIQUE (role, active_from),
    CONSTRAINT check_quota_policy_non_negative_minutes CHECK (monthly_minutes >= 0),
    CONSTRAINT check_quota_policy_positive_weight CHECK (role_weight > 0)
);

CREATE INDEX idx_quota_policy_role_active_from ON quota_policy (role, active_from DESC);

INSERT INTO quota_policy (role, monthly_minutes, role_weight, unlimited, active_from)
VALUES ('STUDENT', 1200, 1, FALSE, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00'),
       ('EMPLOYEE', 4800, 2, FALSE, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00'),
       ('ADMIN', 0, 4, TRUE, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00');

CREATE TABLE user_quota_override
(
    id              UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    user_id         UUID                NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    monthly_minutes BIGINT              NOT NULL DEFAULT 0,
    role_weight     INTEGER             NOT NULL DEFAULT 1,
    unlimited       BOOLEAN             NOT NULL DEFAULT FALSE,
    active_from     TIMESTAMP WITH TIME ZONE    NOT NULL,
    expires_at      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_user_quota_override_non_negative_minutes CHECK (monthly_minutes >= 0),
    CONSTRAINT check_user_quota_override_positive_weight CHECK (role_weight > 0),
    CONSTRAINT check_user_quota_override_dates CHECK (expires_at IS NULL OR expires_at > active_from)
);

CREATE INDEX idx_user_quota_override_user_id ON user_quota_override (user_id);
CREATE INDEX idx_user_quota_override_window ON user_quota_override (user_id, active_from DESC, expires_at DESC);

CREATE TABLE quota_window
(
    id                UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    user_id           UUID                NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    window_start      TIMESTAMP WITH TIME ZONE    NOT NULL,
    window_end        TIMESTAMP WITH TIME ZONE    NOT NULL,
    allocated_minutes BIGINT              NOT NULL,
    reserved_minutes  BIGINT              NOT NULL DEFAULT 0,
    consumed_minutes  BIGINT              NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT              NOT NULL DEFAULT 0,
    CONSTRAINT uq_quota_window_user_window_start UNIQUE (user_id, window_start),
    CONSTRAINT check_quota_window_dates CHECK (window_end > window_start),
    CONSTRAINT check_quota_window_non_negative_allocated CHECK (allocated_minutes >= 0),
    CONSTRAINT check_quota_window_non_negative_reserved CHECK (reserved_minutes >= 0),
    CONSTRAINT check_quota_window_non_negative_consumed CHECK (consumed_minutes >= 0)
);

CREATE INDEX idx_quota_window_user_id ON quota_window (user_id);
CREATE INDEX idx_quota_window_window_start ON quota_window (window_start DESC);

CREATE TABLE quota_ledger
(
    id         UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    user_id    UUID                NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id     UUID REFERENCES jobs (id) ON DELETE SET NULL,
    lease_seq  BIGINT              NOT NULL DEFAULT 0,
    entry_type VARCHAR(40)         NOT NULL,
    minutes    BIGINT              NOT NULL,
    reason     TEXT,
    created_at TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_quota_ledger_minutes_non_negative CHECK (minutes >= 0)
);

CREATE INDEX idx_quota_ledger_user_id_created_at ON quota_ledger (user_id, created_at DESC);
CREATE INDEX idx_quota_ledger_job_id ON quota_ledger (job_id);

ALTER TABLE jobs
    RENAME COLUMN total_cost_credits TO total_consumed_minutes;

ALTER TABLE jobs
    ADD COLUMN queued_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE jobs
    ADD COLUMN env_vars_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN current_lease_reserved_minutes BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lease_sequence BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lease_settled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_jobs_status_created_at ON jobs (status, created_at ASC);
CREATE INDEX idx_jobs_status_queued_at ON jobs (status, queued_at ASC);

UPDATE jobs
SET queued_at = created_at
WHERE status = 'PENDING'
  AND queued_at IS NULL;
