# AI Agent Guide: Private Cloud Resource Manager

This document provides essential context for AI agents working on the Private Cloud Resource Manager.

## Repository Coordinates

- **GitHub Repository**: `karolbystrek/private-cloud-resource-manager`

## 1. System Architecture & Components

The system is a distributed on-premise cloud for batch jobs, enforcing strict billing via a "Pre-Paid Lease" mechanism.

- **Control Plane (Broker)**: Java Spring Boot. Handles API, billing logic, and Nomad orchestration.
- **Frontend**: Next.js + TypeScript. User dashboard and job submission.
- **Data Plane (Worker Nodes)**:
    - **Orchestrator**: HashiCorp Nomad (schedules Docker containers).
    - **Proprietary Agent**: Python. Runs alongside Nomad on workers. **CRITICAL**: Enforces "hard-kill" if lease
      expires.
- **State**:
    - **PostgreSQL**: Permanent store. **MUST** use pessimistic locking for wallet transactions.
    - **Redis**: Distributed locks (mutex) for concurrent lease requests.
    - **MinIO**: S3-compatible storage for job artifacts/logs.

## 2. Critical Business Logic: The Lease Mechanism

The core invariant is **No Unbilled Compute**.

1. **Job Start**: Broker dedicates a 15-minute "Lease" of Compute Units (CUs) upfront.
2. **Execution**: The Worker Agent periodically requests lease renewals from the Broker.
3. **Partition Tolerance**: If the Agent cannot reach the Broker to renew, it **MUST** kill the container when the
   current 15-minute lease expires.
4. **Billing**:
    - Use `SELECT ... FOR UPDATE` on `wallets` table.
    - Record all moves in `cu_ledger` (Append-Only Event Sourcing).
    - `balance_cu` cannot go below zero (`CHECK` constraint).

## 3. Directory Structure & Conventions

- `apps/`: Monorepo root for all services.
    - `broker/`: Java Spring Boot control plane. See `apps/broker/pom.xml`.
    - `frontend/`: Next.js web dashboard. See `apps/frontend/README.md`.
- `docs/`: **Source of Truth**.
- `db/init/`: SQL Schema definitions.
- `compose.yaml`: Local development environment (Postgres, Redis, MinIO).

## 4. Development & Integration

- **Database Changes**: Apply schema updates through versioned Flyway migrations in
  `apps/broker/src/main/resources/db/migration/`.
- **Concurrency**:
    - **Java Broker**: Use Redis for distributed locking to prevent "Thundering Herd" on resource pools.
    - **Database**: Rely on Row-Level Locking for wallet consistency.
- **API Contracts**: Broker is the central authority. Agents and UI are consumers.
- **Authentication**: The Broker uses a hybrid JWT authentication system. A short-lived Access Token is returned in the
  JSON response body to be stored in memory by the client and sent in the Authorization header as a Bearer token. A
  long-lived Refresh Token is issued securely as an `HttpOnly`, `Secure`, and `SameSite=Lax` cookie to protect against
  XSS and CSRF.
- **Frontend**: All interactive interfaces and structural layouts must utilize Shadcn UI components. Use
  `npx shadcn@latest add <component>` to add required primitives to `apps/frontend/src/components/ui/`. Use 'npm'.
  Always use TypeScript types instead of interfaces. Write modular code. It should
  be split logically into files. Adhere to SRP and separation of concerns. The implementation must be clean, modular and
  production grade. Use modern react and next.js features alongside shadcn ui components. Use clear and descriptive
  naming conventions. Do not add comments to the code. The code should be self-explanatory and easy to read. Do not
  write large files. If a file exceeds 200 lines, split it into smaller files. If you need to mutate data (like
  submitting a form to your backend) and then re-fetch it, Server Actions allow you to call server-side code directly
  from client-side interactions (like a button click) without having to manually build an API endpoint. Do not use
  FormEvent type, use SubmitEvent instead.

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
