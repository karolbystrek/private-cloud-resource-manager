CREATE TABLE aggregate_sequences
(
    aggregate_type       VARCHAR(80)              NOT NULL,
    aggregate_id         VARCHAR(255)             NOT NULL,
    next_sequence_number BIGINT                   NOT NULL DEFAULT 1,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (aggregate_type, aggregate_id),
    CONSTRAINT check_aggregate_sequences_positive_next_sequence CHECK (next_sequence_number > 0)
);

CREATE TABLE domain_events
(
    id              UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    event_type      VARCHAR(120)        NOT NULL,
    aggregate_type  VARCHAR(80)         NOT NULL,
    aggregate_id    VARCHAR(255)        NOT NULL,
    sequence_number BIGINT              NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE    NOT NULL,
    actor_type      VARCHAR(40),
    actor_id        UUID,
    user_id         UUID REFERENCES users (id) ON DELETE SET NULL,
    job_id          UUID REFERENCES jobs (id) ON DELETE SET NULL,
    causation_id    UUID,
    correlation_id  UUID                NOT NULL,
    idempotency_key VARCHAR(128),
    source          VARCHAR(80)         NOT NULL,
    payload         JSONB               NOT NULL,
    CONSTRAINT uq_domain_events_aggregate_sequence UNIQUE (aggregate_type, aggregate_id, sequence_number)
);

CREATE INDEX idx_domain_events_aggregate_sequence ON domain_events (aggregate_type, aggregate_id, sequence_number);
CREATE INDEX idx_domain_events_correlation_id ON domain_events (correlation_id);
CREATE INDEX idx_domain_events_type_occurred_at ON domain_events (event_type, occurred_at DESC);
CREATE INDEX idx_domain_events_user_id_occurred_at ON domain_events (user_id, occurred_at DESC)
    WHERE user_id IS NOT NULL;
CREATE INDEX idx_domain_events_job_id_occurred_at ON domain_events (job_id, occurred_at DESC)
    WHERE job_id IS NOT NULL;

CREATE TABLE outbox
(
    id            UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    event_id      UUID                NOT NULL REFERENCES domain_events (id),
    topic         VARCHAR(160)        NOT NULL,
    payload       JSONB               NOT NULL,
    available_at  TIMESTAMP WITH TIME ZONE    NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE,
    claimed_at    TIMESTAMP WITH TIME ZONE,
    claimed_by    VARCHAR(120),
    attempt_count INTEGER             NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMP WITH TIME ZONE    NOT NULL,
    CONSTRAINT uq_outbox_event_topic UNIQUE (event_id, topic),
    CONSTRAINT check_outbox_attempt_count_non_negative CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_unpublished_claim_scan ON outbox (published_at, available_at, created_at);
CREATE INDEX idx_outbox_stuck_claims ON outbox (claimed_at)
    WHERE published_at IS NULL AND claimed_at IS NOT NULL;

CREATE TABLE event_consumer_dedupe
(
    consumer_name VARCHAR(120)             NOT NULL,
    event_id      UUID                     NOT NULL REFERENCES domain_events (id),
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);
