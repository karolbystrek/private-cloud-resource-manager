CREATE TABLE quota_policy
(
    id              UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
    role            VARCHAR(20) NOT NULL,
    monthly_minutes BIGINT      NOT NULL DEFAULT 0,
    role_weight     INTEGER     NOT NULL DEFAULT 1,
    unlimited       BOOLEAN     NOT NULL DEFAULT FALSE,
    active_from     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quota_policy_role_active_from UNIQUE (role, active_from),
    CONSTRAINT chk_quota_policy_minutes_nn CHECK (monthly_minutes >= 0),
    CONSTRAINT chk_quota_policy_weight_pos CHECK (role_weight > 0)
);

CREATE INDEX idx_quota_policy_role_active_from ON quota_policy (role, active_from DESC);

INSERT INTO quota_policy (role, monthly_minutes, role_weight, unlimited, active_from)
VALUES ('STUDENT', 1200, 1, FALSE, TIMESTAMPTZ '1970-01-01 00:00:00+00'),
       ('EMPLOYEE', 4800, 2, FALSE, TIMESTAMPTZ '1970-01-01 00:00:00+00'),
       ('ADMIN', 0, 4, TRUE, TIMESTAMPTZ '1970-01-01 00:00:00+00');
