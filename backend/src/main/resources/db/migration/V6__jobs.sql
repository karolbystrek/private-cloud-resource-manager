CREATE TABLE jobs
(
    id                               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    profile_id                       UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    node_id                          VARCHAR(128) REFERENCES nodes (id),
    status                           VARCHAR(40)  NOT NULL DEFAULT 'SUBMITTED',
    docker_image                     VARCHAR(255) NOT NULL,
    execution_command                TEXT         NOT NULL,
    idempotency_key                  VARCHAR(64),
    submission_fingerprint           CHAR(64),
    req_cpu_cores                    INTEGER      NOT NULL,
    req_ram_gb                       INTEGER      NOT NULL,
    total_consumed_minutes           BIGINT       NOT NULL DEFAULT 0,
    env_vars_json                    TEXT         NOT NULL DEFAULT '{}',
    queued_at                        TIMESTAMPTZ,
    started_at                       TIMESTAMPTZ,
    finished_at                      TIMESTAMPTZ,
    active_lease_expires_at          TIMESTAMPTZ,
    current_lease_reserved_minutes   BIGINT       NOT NULL DEFAULT 0,
    lease_sequence                   BIGINT       NOT NULL DEFAULT 0,
    lease_settled                    BOOLEAN      NOT NULL DEFAULT FALSE,
    current_run_id                   UUID,
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_jobs_idempotency_pair CHECK (
            (idempotency_key IS NULL AND submission_fingerprint IS NULL)
                OR (idempotency_key IS NOT NULL AND submission_fingerprint IS NOT NULL)
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
