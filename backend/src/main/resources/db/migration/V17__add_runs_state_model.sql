ALTER TABLE jobs
    ALTER COLUMN status TYPE VARCHAR(40),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN current_run_id UUID;

CREATE TABLE runs
(
    id                             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id                         UUID        NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    user_id                        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    run_number                     INTEGER     NOT NULL,
    status                         VARCHAR(40) NOT NULL,
    resource_class                 VARCHAR(60),
    requested_timeout_minutes      BIGINT,
    quota_reservation_id           UUID,
    nomad_job_id                   VARCHAR(180),
    nomad_eval_id                  VARCHAR(180),
    nomad_allocation_id            VARCHAR(180),
    queued_at                      TIMESTAMPTZ,
    dispatch_requested_at          TIMESTAMPTZ,
    dispatched_at                  TIMESTAMPTZ,
    started_at                     TIMESTAMPTZ,
    process_finished_at            TIMESTAMPTZ,
    finalized_at                   TIMESTAMPTZ,
    active_lease_expires_at        TIMESTAMPTZ,
    current_lease_reserved_minutes BIGINT      NOT NULL DEFAULT 0,
    lease_sequence                 BIGINT      NOT NULL DEFAULT 0,
    lease_settled                  BOOLEAN     NOT NULL DEFAULT FALSE,
    total_consumed_minutes         BIGINT      NOT NULL DEFAULT 0,
    terminal_reason                VARCHAR(120),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_runs_job_run_number UNIQUE (job_id, run_number),
    CONSTRAINT check_runs_positive_run_number CHECK (run_number > 0),
    CONSTRAINT check_runs_non_negative_reserved_minutes CHECK (current_lease_reserved_minutes >= 0),
    CONSTRAINT check_runs_non_negative_lease_sequence CHECK (lease_sequence >= 0),
    CONSTRAINT check_runs_non_negative_consumed_minutes CHECK (total_consumed_minutes >= 0),
    CONSTRAINT check_runs_status CHECK (
        status IN (
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
CREATE INDEX idx_runs_user_created_at ON runs (user_id, created_at DESC);
CREATE INDEX idx_runs_status_queued_at ON runs (status, queued_at ASC, created_at ASC);
CREATE UNIQUE INDEX uq_runs_nomad_job_id ON runs (nomad_job_id) WHERE nomad_job_id IS NOT NULL;
CREATE UNIQUE INDEX uq_runs_nomad_allocation_id ON runs (nomad_allocation_id) WHERE nomad_allocation_id IS NOT NULL;

INSERT INTO runs (
    id,
    job_id,
    user_id,
    run_number,
    status,
    queued_at,
    started_at,
    process_finished_at,
    active_lease_expires_at,
    current_lease_reserved_minutes,
    lease_sequence,
    lease_settled,
    total_consumed_minutes,
    terminal_reason,
    created_at,
    updated_at
)
SELECT uuid_generate_v4(),
       id,
       user_id,
       1,
       CASE status
           WHEN 'QUEUED' THEN 'QUEUED'
           WHEN 'PENDING' THEN 'SCHEDULING'
           WHEN 'RUNNING' THEN 'RUNNING'
           WHEN 'COMPLETED' THEN 'SUCCEEDED'
           WHEN 'FAILED' THEN 'FAILED'
           WHEN 'OOM_KILLED' THEN 'FAILED'
           WHEN 'LEASE_EXPIRED' THEN 'TIMED_OUT'
           WHEN 'STOPPED' THEN 'CANCELED'
           ELSE 'FAILED'
           END,
       queued_at,
       started_at,
       finished_at,
       active_lease_expires_at,
       current_lease_reserved_minutes,
       lease_sequence,
       lease_settled,
       total_consumed_minutes,
       CASE status
           WHEN 'OOM_KILLED' THEN 'OOM_KILLED'
           WHEN 'LEASE_EXPIRED' THEN 'LEASE_EXPIRED'
           WHEN 'STOPPED' THEN 'STOPPED'
           ELSE NULL
           END,
       created_at,
       now()
FROM jobs;

UPDATE jobs
SET current_run_id = runs.id,
    status = runs.status,
    updated_at = now()
FROM runs
WHERE runs.job_id = jobs.id
  AND runs.run_number = 1;

ALTER TABLE jobs
    ADD CONSTRAINT fk_jobs_current_run_id FOREIGN KEY (current_run_id) REFERENCES runs (id);

CREATE INDEX idx_jobs_current_run_id ON jobs (current_run_id) WHERE current_run_id IS NOT NULL;

ALTER TABLE quota_ledger
    ADD COLUMN run_id UUID REFERENCES runs (id) ON DELETE SET NULL;

UPDATE quota_ledger
SET run_id = runs.id
FROM runs
WHERE quota_ledger.job_id = runs.job_id
  AND runs.run_number = 1;

CREATE INDEX idx_quota_ledger_run_id ON quota_ledger (run_id) WHERE run_id IS NOT NULL;
