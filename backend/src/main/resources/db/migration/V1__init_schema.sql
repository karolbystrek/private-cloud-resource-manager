CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ------------------------------------------------------------
-- 1. Users
-- ------------------------------------------------------------
CREATE TABLE users
(
    id         UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    username   VARCHAR(50) UNIQUE  NOT NULL,
    email      VARCHAR(255) UNIQUE NOT NULL,
    role       VARCHAR(20)         NOT NULL DEFAULT 'STUDENT', -- 'STUDENT', 'RESEARCHER', 'ADMIN'
    created_at TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- 2. Wallets
--    Balance updated exclusively via SELECT ... FOR UPDATE to
--    prevent Read-Modify-Write anomalies (pessimistic locking).
-- ------------------------------------------------------------
CREATE TABLE wallets
(
    id         UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    user_id    UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    balance_cu NUMERIC(10, 4) NOT NULL DEFAULT 0.0000,
    CONSTRAINT check_positive_balance CHECK (balance_cu >= 0)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

-- ------------------------------------------------------------
-- 3. Nodes (physical lab machines)
--    Periodically updated by the proprietary Agent heartbeat.
-- ------------------------------------------------------------
CREATE TABLE nodes
(
    id              UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    hostname        VARCHAR(100) UNIQUE NOT NULL,
    ip_address      INET                NOT NULL,
    status          VARCHAR(20)         NOT NULL DEFAULT 'AVAILABLE', -- 'AVAILABLE', 'IN_USE', 'OFFLINE', 'MAINTENANCE'
    total_cpu_cores INTEGER             NOT NULL,
    total_ram_gb    INTEGER             NOT NULL,
    total_gpu_count INTEGER             NOT NULL DEFAULT 0,
    agent_version   VARCHAR(20)         NOT NULL,
    last_heartbeat  TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nodes_status ON nodes (status);

-- ------------------------------------------------------------
-- 4. Jobs (Docker tasks scheduled via Nomad)
--    active_lease_expires_at: tracks the current 15-minute
--    pre-paid chunk. The Agent hard-kills the container if
--    this timestamp passes without a successful renewal.
-- ------------------------------------------------------------
CREATE TABLE jobs
(
    id                      UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    user_id                 UUID         NOT NULL REFERENCES users (id),
    node_id                 UUID REFERENCES nodes (id),                 -- Nullable until Nomad schedules the job
    status                  VARCHAR(20)  NOT NULL    DEFAULT 'PENDING', -- 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'OOM_KILLED', 'LEASE_EXPIRED'
    docker_image            VARCHAR(255) NOT NULL,
    execution_command       TEXT         NOT NULL,
    req_cpu_cores           INTEGER      NOT NULL,
    req_ram_gb              INTEGER      NOT NULL,
    req_gpu_count           INTEGER      NOT NULL    DEFAULT 0,
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE,
    active_lease_expires_at TIMESTAMP WITH TIME ZONE,                   -- Expiry of the current 15-minute pre-paid chunk
    total_cost_cu           NUMERIC(10, 4)           DEFAULT 0.0000,
    minio_artifact_url      VARCHAR(512),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jobs_user_id ON jobs (user_id);
CREATE INDEX idx_jobs_status ON jobs (status);

-- ------------------------------------------------------------
-- 5. CU Transaction Ledger (append-only / event-sourced)
--    LEASE_DEDUCTION: pre-paid 15-min chunk deducted upfront.
--    LEASE_REFUND:    issued for unused time after job ends.
--    SEMESTER_ALLOCATION: admin top-up (job_id is NULL).
-- ------------------------------------------------------------
CREATE TABLE cu_ledger
(
    id               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    wallet_id        UUID           NOT NULL REFERENCES wallets (id),
    job_id           UUID REFERENCES jobs (id), -- NULL for admin allocations
    transaction_type VARCHAR(30)    NOT NULL,   -- 'SEMESTER_ALLOCATION', 'LEASE_DEDUCTION', 'LEASE_REFUND'
    amount_cu        NUMERIC(10, 4) NOT NULL,   -- Positive for allocations/refunds, negative for deductions
    description      TEXT,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cu_ledger_wallet_id ON cu_ledger (wallet_id);
CREATE INDEX idx_cu_ledger_job_id ON cu_ledger (job_id);
