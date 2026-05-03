CREATE TABLE idempotency_records
(
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id           UUID,
    actor_type          VARCHAR(40)  NOT NULL,
    actor_id            VARCHAR(255) NOT NULL,
    workflow            VARCHAR(120) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64)     NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    locked_until        TIMESTAMPTZ,
    response_status     INTEGER,
    response_body       JSONB,
    resource_type       VARCHAR(80),
    resource_id         UUID,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT check_idempotency_records_status CHECK (
        status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED_RETRYABLE', 'FAILED_FINAL')
        ),
    CONSTRAINT check_idempotency_records_completed_response CHECK (
        status <> 'COMPLETED'
            OR (response_status IS NOT NULL AND response_body IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_idempotency_records_tenantless_scope
    ON idempotency_records (actor_type, actor_id, workflow, idempotency_key)
    WHERE tenant_id IS NULL;

CREATE UNIQUE INDEX uq_idempotency_records_tenant_scope
    ON idempotency_records (tenant_id, actor_type, actor_id, workflow, idempotency_key)
    WHERE tenant_id IS NOT NULL;

CREATE INDEX idx_idempotency_records_status_locked_until
    ON idempotency_records (status, locked_until);

CREATE INDEX idx_idempotency_records_resource
    ON idempotency_records (resource_type, resource_id)
    WHERE resource_type IS NOT NULL AND resource_id IS NOT NULL;
