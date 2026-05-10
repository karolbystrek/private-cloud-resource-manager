ALTER TABLE runs
    ADD COLUMN dispatch_attempt_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_dispatch_error TEXT;
