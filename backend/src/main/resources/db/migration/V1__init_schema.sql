CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Domain profile (role only). Identity lives in auth.users (Supabase GoTrue).
CREATE TABLE profiles
(
    id         UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    role       VARCHAR(20)                NOT NULL DEFAULT 'STUDENT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wallets
(
    id         UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    user_id    UUID           NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    balance_cu NUMERIC(10, 4) NOT NULL DEFAULT 0.0000,
    CONSTRAINT check_positive_balance CHECK (balance_cu >= 0)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

CREATE TABLE nodes
(
    id              UUID PRIMARY KEY             DEFAULT uuid_generate_v4(),
    hostname        VARCHAR(100) UNIQUE NOT NULL,
    ip_address      INET                NOT NULL,
    status          VARCHAR(20)         NOT NULL DEFAULT 'AVAILABLE',
    total_cpu_cores INTEGER             NOT NULL,
    total_ram_gb    INTEGER             NOT NULL,
    total_gpu_count INTEGER             NOT NULL DEFAULT 0,
    agent_version   VARCHAR(20)         NOT NULL,
    last_heartbeat  TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nodes_status ON nodes (status);

CREATE TABLE jobs
(
    id                      UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    user_id                 UUID         NOT NULL REFERENCES auth.users (id),
    node_id                 UUID REFERENCES nodes (id),
    status                  VARCHAR(20)  NOT NULL    DEFAULT 'PENDING',
    docker_image            VARCHAR(255) NOT NULL,
    execution_command       TEXT         NOT NULL,
    req_cpu_cores           INTEGER      NOT NULL,
    req_ram_gb              INTEGER      NOT NULL,
    req_gpu_count           INTEGER      NOT NULL    DEFAULT 0,
    started_at              TIMESTAMP WITH TIME ZONE,
    finished_at             TIMESTAMP WITH TIME ZONE,
    active_lease_expires_at TIMESTAMP WITH TIME ZONE,
    total_cost_cu           NUMERIC(10, 4)           DEFAULT 0.0000,
    minio_artifact_url      VARCHAR(512),
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jobs_user_id ON jobs (user_id);
CREATE INDEX idx_jobs_status ON jobs (status);

CREATE TABLE cu_ledger
(
    id               UUID PRIMARY KEY         DEFAULT uuid_generate_v4(),
    wallet_id        UUID           NOT NULL REFERENCES wallets (id),
    job_id           UUID REFERENCES jobs (id),
    transaction_type VARCHAR(30)    NOT NULL,
    amount_cu        NUMERIC(10, 4) NOT NULL,
    description      TEXT,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cu_ledger_wallet_id ON cu_ledger (wallet_id);
CREATE INDEX idx_cu_ledger_job_id ON cu_ledger (job_id);
