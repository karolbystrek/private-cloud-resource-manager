CREATE TABLE job_artifacts
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id          UUID         NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    profile_id      UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    status          VARCHAR(40)  NOT NULL,
    object_key      TEXT         NOT NULL,
    size_bytes      BIGINT,
    checksum_sha256 VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at    TIMESTAMPTZ,
    failure_reason  TEXT,
    CONSTRAINT chk_job_artifacts_status CHECK (
        status IN ('PENDING', 'UPLOADING', 'AVAILABLE', 'MISSING', 'FAILED')
        ),
    CONSTRAINT chk_job_artifacts_size_nn CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE UNIQUE INDEX uq_job_artifacts_job_id ON job_artifacts (job_id);

CREATE INDEX idx_job_artifacts_profile_id ON job_artifacts (profile_id);

CREATE INDEX idx_job_artifacts_status_created_at ON job_artifacts (status, created_at);
