CREATE TABLE outbox
(
    id            UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    event_id      UUID         NOT NULL REFERENCES domain_events (id),
    topic         VARCHAR(160) NOT NULL,
    payload       JSONB        NOT NULL,
    headers       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    available_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ,
    claimed_at    TIMESTAMPTZ,
    claimed_by    VARCHAR(120),
    attempt_count INTEGER      NOT NULL DEFAULT 0,
    last_error    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_outbox_event_topic UNIQUE (event_id, topic),
    CONSTRAINT chk_outbox_attempt_count_nn CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_ready_to_publish ON outbox (available_at, created_at, id)
    WHERE published_at IS NULL AND claimed_at IS NULL;
CREATE INDEX idx_outbox_stuck_claims ON outbox (claimed_at)
    WHERE published_at IS NULL AND claimed_at IS NOT NULL;

CREATE TABLE event_consumer_dedupe
(
    consumer_name VARCHAR(120) NOT NULL,
    source        VARCHAR(120) NOT NULL,
    event_id      UUID         NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_name, source, event_id)
);
