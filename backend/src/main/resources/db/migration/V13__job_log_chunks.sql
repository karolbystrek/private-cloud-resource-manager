CREATE TABLE job_log_streams
(
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id           UUID        NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    profile_id       UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    stream           VARCHAR(20) NOT NULL,
    last_offset      BIGINT      NOT NULL DEFAULT 0,
    capture_complete BOOLEAN     NOT NULL DEFAULT FALSE,
    last_error       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_job_log_streams_stream CHECK (stream IN ('stdout', 'stderr')),
    CONSTRAINT chk_job_log_streams_offset_nn CHECK (last_offset >= 0)
);

CREATE UNIQUE INDEX uq_job_log_streams_job_stream ON job_log_streams (job_id, stream);

CREATE INDEX idx_job_log_streams_profile_id ON job_log_streams (profile_id);

CREATE INDEX idx_job_log_streams_capture ON job_log_streams (capture_complete, updated_at);

CREATE TABLE job_log_chunks
(
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id    UUID        NOT NULL REFERENCES job_log_streams (id) ON DELETE CASCADE,
    job_id       UUID        NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    profile_id   UUID        NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    stream       VARCHAR(20) NOT NULL,
    sequence     BIGINT      NOT NULL,
    object_key   TEXT        NOT NULL,
    offset_start BIGINT      NOT NULL,
    offset_end   BIGINT      NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_job_log_chunks_stream CHECK (stream IN ('stdout', 'stderr')),
    CONSTRAINT chk_job_log_chunks_sequence_nn CHECK (sequence >= 0),
    CONSTRAINT chk_job_log_chunks_offsets CHECK (offset_start >= 0 AND offset_end >= offset_start),
    CONSTRAINT chk_job_log_chunks_size_nn CHECK (size_bytes >= 0)
);

CREATE UNIQUE INDEX uq_job_log_chunks_stream_sequence ON job_log_chunks (stream_id, sequence);

CREATE INDEX idx_job_log_chunks_job_stream_sequence ON job_log_chunks (job_id, stream, sequence);

CREATE INDEX idx_job_log_chunks_profile_id ON job_log_chunks (profile_id);
