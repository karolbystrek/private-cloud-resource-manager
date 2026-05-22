CREATE TABLE aggregate_sequences
(
    aggregate_type       VARCHAR(80)  NOT NULL,
    aggregate_id         VARCHAR(255) NOT NULL,
    next_sequence_number BIGINT       NOT NULL DEFAULT 1,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (aggregate_type, aggregate_id),
    CONSTRAINT chk_aggregate_sequences_positive_next CHECK (next_sequence_number > 0)
);

CREATE TABLE domain_events
(
    id              UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    event_type      VARCHAR(120) NOT NULL,
    aggregate_type  VARCHAR(80)  NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    sequence_number BIGINT       NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    schema_version  INTEGER      NOT NULL DEFAULT 1,
    actor_type      VARCHAR(40),
    actor_id        VARCHAR(255),
    profile_id      UUID,
    job_id          UUID,
    causation_id    UUID,
    correlation_id  UUID         NOT NULL,
    idempotency_key VARCHAR(128),
    source          VARCHAR(80)  NOT NULL,
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    payload         JSONB        NOT NULL,
    CONSTRAINT uq_domain_events_aggregate_sequence UNIQUE (aggregate_type, aggregate_id, sequence_number)
);

CREATE INDEX idx_domain_events_aggregate_sequence ON domain_events (aggregate_type, aggregate_id, sequence_number);
CREATE INDEX idx_domain_events_correlation_id ON domain_events (correlation_id);
CREATE INDEX idx_domain_events_type_occurred_at ON domain_events (event_type, occurred_at DESC);
CREATE INDEX idx_domain_events_profile_occurred_at ON domain_events (profile_id, occurred_at DESC)
    WHERE profile_id IS NOT NULL;
CREATE INDEX idx_domain_events_job_occurred_at ON domain_events (job_id, occurred_at DESC)
    WHERE job_id IS NOT NULL;
