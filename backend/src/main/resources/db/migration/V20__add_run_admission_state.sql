ALTER TABLE runs
    DROP CONSTRAINT check_runs_status;

ALTER TABLE runs
    ADD CONSTRAINT check_runs_status CHECK (
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
        );

CREATE INDEX idx_runs_user_status ON runs (user_id, status);
