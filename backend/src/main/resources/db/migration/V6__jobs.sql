CREATE TABLE jobs
(
    id                             UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    profile_id                     UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    status                         VARCHAR(40)  NOT NULL DEFAULT 'SUBMITTED',
    docker_image                   VARCHAR(255) NOT NULL,
    execution_command              TEXT         NOT NULL,
    idempotency_key                VARCHAR(64),
    submission_fingerprint         CHAR(64),
    req_cpu_cores                  INTEGER      NOT NULL,
    req_ram_gb                     INTEGER      NOT NULL,
    total_consumed_minutes         BIGINT       NOT NULL DEFAULT 0,
    env_vars_json                  TEXT         NOT NULL DEFAULT '{}',
    queued_at                      TIMESTAMPTZ,
    dispatch_requested_at          TIMESTAMPTZ,
    dispatched_at                  TIMESTAMPTZ,
    started_at                     TIMESTAMPTZ,
    process_finished_at            TIMESTAMPTZ,
    finalized_at                   TIMESTAMPTZ,
    active_lease_expires_at        TIMESTAMPTZ,
    current_lease_reserved_minutes BIGINT       NOT NULL DEFAULT 0,
    lease_sequence                 BIGINT       NOT NULL DEFAULT 0,
    lease_settled                  BOOLEAN      NOT NULL DEFAULT FALSE,
    lease_renewal_attempt_count    BIGINT       NOT NULL DEFAULT 0,
    last_lease_renewal_error       TEXT,
    lease_stop_requested_at        TIMESTAMPTZ,
    terminal_reason                VARCHAR(120),
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_jobs_idempotency_pair CHECK (
        (idempotency_key IS NULL AND submission_fingerprint IS NULL)
            OR (idempotency_key IS NOT NULL AND submission_fingerprint IS NOT NULL)
        ),
    CONSTRAINT chk_jobs_reserved_minutes_nn CHECK (current_lease_reserved_minutes >= 0),
    CONSTRAINT chk_jobs_lease_sequence_nn CHECK (lease_sequence >= 0),
    CONSTRAINT chk_jobs_consumed_nn CHECK (total_consumed_minutes >= 0),
    CONSTRAINT chk_jobs_lease_renewal_attempt_count_nn CHECK (lease_renewal_attempt_count >= 0),
    CONSTRAINT chk_jobs_status CHECK (
        status IN (
                   'SUBMITTED',
                   'QUEUED',
                   'DISPATCHING',
                   'SCHEDULING',
                   'RUNNING',
                   'FINALIZING',
                   'SUCCEEDED',
                   'FAILED',
                   'CANCELED',
                   'TIMED_OUT',
                   'INFRA_FAILED'
            )
        )
);

CREATE UNIQUE INDEX uq_jobs_profile_idempotency_key
    ON jobs (profile_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_jobs_profile_id ON jobs (profile_id);

CREATE INDEX idx_jobs_profile_created_at
    ON jobs (profile_id, created_at DESC);

CREATE INDEX idx_jobs_status ON jobs (status);

CREATE INDEX idx_jobs_status_created_at ON jobs (status, created_at ASC);

CREATE INDEX idx_jobs_status_queued_at ON jobs (status, queued_at ASC);

CREATE INDEX idx_jobs_profile_status ON jobs (profile_id, status);

CREATE INDEX idx_jobs_active_lease_enforcement
    ON jobs (active_lease_expires_at, status)
    WHERE active_lease_expires_at IS NOT NULL
        AND lease_settled = FALSE
        AND status IN ('QUEUED', 'DISPATCHING', 'SCHEDULING', 'RUNNING', 'FINALIZING');
