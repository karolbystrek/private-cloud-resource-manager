CREATE TABLE runs
(
    id                               UUID PRIMARY KEY     DEFAULT uuid_generate_v4(),
    job_id                           UUID        NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    profile_id                       UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    run_number                       INTEGER     NOT NULL,
    status                           VARCHAR(40) NOT NULL,
    resource_class                   VARCHAR(60),
    requested_timeout_minutes        BIGINT,
    quota_reservation_id             UUID,
    nomad_job_id                     VARCHAR(180),
    nomad_eval_id                    VARCHAR(180),
    nomad_allocation_id              VARCHAR(180),
    queued_at                        TIMESTAMPTZ,
    dispatch_requested_at            TIMESTAMPTZ,
    dispatched_at                    TIMESTAMPTZ,
    started_at                       TIMESTAMPTZ,
    process_finished_at              TIMESTAMPTZ,
    finalized_at                     TIMESTAMPTZ,
    active_lease_expires_at          TIMESTAMPTZ,
    current_lease_reserved_minutes   BIGINT      NOT NULL DEFAULT 0,
    lease_sequence                   BIGINT      NOT NULL DEFAULT 0,
    lease_settled                    BOOLEAN     NOT NULL DEFAULT FALSE,
    total_consumed_minutes           BIGINT      NOT NULL DEFAULT 0,
    terminal_reason                  VARCHAR(120),
    dispatch_attempt_count           BIGINT      NOT NULL DEFAULT 0,
    last_dispatch_error              TEXT,
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_runs_job_run_number UNIQUE (job_id, run_number),
    CONSTRAINT chk_runs_positive_run_number CHECK (run_number > 0),
    CONSTRAINT chk_runs_reserved_minutes_nn CHECK (current_lease_reserved_minutes >= 0),
    CONSTRAINT chk_runs_lease_sequence_nn CHECK (lease_sequence >= 0),
    CONSTRAINT chk_runs_consumed_nn CHECK (total_consumed_minutes >= 0),
    CONSTRAINT chk_dispatch_attempt_count_nn CHECK (dispatch_attempt_count >= 0),
    CONSTRAINT chk_runs_status CHECK (
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

CREATE INDEX idx_runs_job_id ON runs (job_id);
CREATE INDEX idx_runs_profile_created_at ON runs (profile_id, created_at DESC);
CREATE INDEX idx_runs_status_queued_at ON runs (status, queued_at ASC, created_at ASC);
CREATE UNIQUE INDEX uq_runs_nomad_job_id ON runs (nomad_job_id) WHERE nomad_job_id IS NOT NULL;
CREATE UNIQUE INDEX uq_runs_nomad_allocation_id ON runs (nomad_allocation_id)
    WHERE nomad_allocation_id IS NOT NULL;
CREATE INDEX idx_runs_profile_status ON runs (profile_id, status);

ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_current_run_id FOREIGN KEY (current_run_id) REFERENCES runs (id);

CREATE INDEX idx_jobs_current_run_id ON jobs (current_run_id)
    WHERE current_run_id IS NOT NULL;
