# AI Agent Guide: Private Cloud Resource Manager

This document provides essential context for AI agents working on the Private Cloud Resource Manager.

## 1. System Architecture & Components

The system is a distributed on-premise cloud for batch jobs, enforcing strict billing via a "Pre-Paid Lease" mechanism.

- **Control Plane (Broker)**: Java Spring Boot. Handles API, billing logic, and Nomad orchestration.
- **Frontend**: Next.js + TypeScript. User dashboard and job submission.
- **Data Plane (Worker Nodes)**:
  - **Orchestrator**: HashiCorp Nomad (schedules Docker containers).
  - **Proprietary Agent**: Python/Java. Runs alongside Nomad on workers. **CRITICAL**: Enforces "hard-kill" if lease expires.
- **State**:
  - **PostgreSQL**: Permanent store. **MUST** use pessimistic locking for wallet transactions.
  - **Redis**: Distributed locks (mutex) for concurrent lease requests.
  - **MinIO**: S3-compatible storage for job artifacts/logs.

## 2. Critical Business Logic: The Lease Mechanism

The core invariant is **No Unbilled Compute**.

1. **Job Start**: Broker dedicates a 15-minute "Lease" of Compute Units (CUs) upfront.
2. **Execution**: The Worker Agent periodically requests lease renewals from the Broker.
3. **Partition Tolerance**: If the Agent cannot reach the Broker to renew, it **MUST** kill the container when the current 15-minute lease expires.
4. **Billing**:
   - Use `SELECT ... FOR UPDATE` on `wallets` table.
   - Record all moves in `cu_ledger` (Append-Only Event Sourcing).
   - `balance_cu` cannot go below zero (`CHECK` constraint).

## 3. Directory Structure & Conventions

- `docs/`: **Source of Truth**. See `PROJECT_PLAN.md` and `DATABASE_SCHEMA_PLAN.md` before architectural changes.
- `db/init/`: SQL Schema definitions.
- `compose.yml`: Local development environment (Postgres, Redis, MinIO).

## 4. Development & Integration

- **Database Changes**: Always update `db/init/01_schema.sql` and `docs/DATABASE_SCHEMA_PLAN.md` in tandem.
- **Concurrency**:
  - **Java Broker**: Use Redis for distributed locking to prevent "Thundering Herd" on resource pools.
  - **Database**: Rely on Row-Level Locking for wallet consistency.
- **API Contracts**: Broker is the central authority. Agents and UI are consumers.

## 5. Common Patterns

- **Ledger Entries**: Never update a ledger row. Always `INSERT` a new transaction (Debit/Credit/Refund).
- **UUIDs**: Use `uuid-ossp` for all primary keys.

## 6. Version Control Workflow

- **Branching Strategy**:
  - Default: Work directly on `main` branch.
  - Push: Push directly to `main` upon completion, unless instructed otherwise.
- **Commit Convention**:
  - **Style**: Strictly follow **Conventional Commits** (e.g., `feat:`, `fix:`, `chore:`, `docs:`).
  - **Atomicity**: Keep commits small and focused. Do not combine unrelated changes.
- **Issue References**:
  - **Protocol**: Before committing, **ask the user** for a GitHub Issue Reference.
    - If provided: Include it in the commit footer or subject (e.g., `Ref: #123`).
    - If user says "no issue": Proceed without it.
