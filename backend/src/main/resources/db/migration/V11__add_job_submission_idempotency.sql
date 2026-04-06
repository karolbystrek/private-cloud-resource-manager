ALTER TABLE jobs
    ADD COLUMN idempotency_key VARCHAR(64),
    ADD COLUMN submission_fingerprint CHAR(64);

ALTER TABLE jobs
    ADD CONSTRAINT check_jobs_submission_idempotency_columns
        CHECK (
            (idempotency_key IS NULL AND submission_fingerprint IS NULL)
                OR (idempotency_key IS NOT NULL AND submission_fingerprint IS NOT NULL)
            );

CREATE UNIQUE INDEX uq_jobs_user_idempotency_key
    ON jobs (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
