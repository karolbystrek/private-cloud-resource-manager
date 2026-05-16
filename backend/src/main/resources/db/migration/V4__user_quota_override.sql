CREATE TABLE user_quota_override
(
    id               UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    profile_id       UUID                         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    monthly_minutes  BIGINT                       NOT NULL DEFAULT 0,
    role_weight      INTEGER                      NOT NULL DEFAULT 1,
    unlimited        BOOLEAN                      NOT NULL DEFAULT FALSE,
    active_from      TIMESTAMPTZ                  NOT NULL,
    expires_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ                  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_quota_override_minutes_nn CHECK (monthly_minutes >= 0),
    CONSTRAINT chk_user_quota_override_weight_pos CHECK (role_weight > 0),
    CONSTRAINT chk_user_quota_override_dates CHECK (expires_at IS NULL OR expires_at > active_from)
);

CREATE INDEX idx_user_quota_override_profile_id ON user_quota_override (profile_id);
CREATE INDEX idx_user_quota_override_profile_window ON user_quota_override (profile_id, active_from DESC, expires_at DESC);
