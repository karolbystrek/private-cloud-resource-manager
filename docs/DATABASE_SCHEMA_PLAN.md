### 1. Core Users and Wallets

The user and wallet data are separated. The wallet acts as a cached state of the user's available Compute Units (CU).

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT', -- 'STUDENT', 'RESEARCHER', 'ADMIN'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    balance_cu NUMERIC(10, 4) NOT NULL DEFAULT 0.0000,
    CONSTRAINT check_positive_balance CHECK (balance_cu >= 0)
);

CREATE INDEX idx_wallets_user_id ON wallets(user_id);

```

### 2. Infrastructure and Nodes

This table represents the physical laboratory machines. Since your proprietary execution agent runs directly on these computers, it will periodically report its status and hardware capacity to update these records.

```sql
CREATE TABLE nodes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hostname VARCHAR(100) UNIQUE NOT NULL,
    ip_address INET NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE', -- 'AVAILABLE', 'IN_USE', 'OFFLINE', 'MAINTENANCE'
    total_cpu_cores INTEGER NOT NULL,
    total_ram_gb INTEGER NOT NULL,
    total_gpu_count INTEGER NOT NULL DEFAULT 0,
    cu_price_per_hour NUMERIC(10, 4) NOT NULL,
    agent_version VARCHAR(20) NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nodes_status ON nodes(status);

```

### 3. Jobs (Tasks)

This tracks the state of the Docker containers scheduled via Nomad. It stores the metadata required for execution and the location of the resulting artifacts.

```sql
CREATE TABLE jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id),
    node_id UUID REFERENCES nodes(id), -- Nullable initially until Nomad schedules it
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'OOM_KILLED'
    docker_image VARCHAR(255) NOT NULL,
    execution_command TEXT NOT NULL,
    req_cpu_cores INTEGER NOT NULL,
    req_ram_gb INTEGER NOT NULL,
    req_gpu_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    total_cost_cu NUMERIC(10, 4) DEFAULT 0.0000,
    minio_artifact_url VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jobs_user_id ON jobs(user_id);
CREATE INDEX idx_jobs_status ON jobs(status);

```

### 4. Transaction Ledger (Event Sourcing)

To maintain strict financial consistency, the system must use an append-only ledger. When the proprietary agent sends a 5-minute heartbeat confirming task execution, the Java Broker inserts a record here and updates the `wallets` table within a single ACID transaction.

```sql
CREATE TABLE cu_ledger (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    job_id UUID REFERENCES jobs(id), -- Null if the transaction is an admin allocation
    transaction_type VARCHAR(30) NOT NULL, -- 'SEMESTER_ALLOCATION', 'USAGE_DEDUCTION', 'REFUND'
    amount_cu NUMERIC(10, 4) NOT NULL, -- Positive for allocations/refunds, negative for usage
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cu_ledger_wallet_id ON cu_ledger(wallet_id);
CREATE INDEX idx_cu_ledger_job_id ON cu_ledger(job_id);

```

---

### Key Architectural Safeguards in this Schema:

* **`CHECK (balance_cu >= 0)`**: This database-level constraint acts as the ultimate fail-safe. Even if the application logic contains a race condition, PostgreSQL will abort any transaction that would drive a student's balance below zero.
* **Append-Only Ledger**: The `cu_ledger` table guarantees an immutable audit trail. If the agent loses connection (network partition) and later submits a bulk time report, it will be inserted here as a single localized deduction, easily verified against the job duration.
* **Separation of Concerns**: The `jobs` table tracks execution metadata, while the `cu_ledger` handles the billing state.
