ALTER TABLE runs
    ADD COLUMN lease_renewal_attempt_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_lease_renewal_error TEXT,
    ADD COLUMN lease_stop_requested_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_runs_lease_renewal_attempt_count_nn CHECK (lease_renewal_attempt_count >= 0);

CREATE INDEX idx_runs_active_lease_enforcement
    ON runs (active_lease_expires_at, status)
    WHERE active_lease_expires_at IS NOT NULL
      AND lease_settled = FALSE
      AND status IN ('QUEUED', 'DISPATCHING', 'SCHEDULING', 'RUNNING', 'FINALIZING');
