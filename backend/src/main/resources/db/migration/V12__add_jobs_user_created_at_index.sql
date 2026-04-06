CREATE INDEX IF NOT EXISTS idx_jobs_user_created_at
    ON jobs (user_id, created_at DESC);
