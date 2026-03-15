# Development Roadmap

This document outlines the step-by-step implementation plan for the Private Cloud Resource Manager (PCRM). It is
designed for a **4-person team** working in a **Monorepo** structure.

---

## Milestone 1: Monorepo & Infrastructure (Weeks 1-2)

**Goal**: A single command `docker-compose up` spins up the entire simulated cloud (Database, Broker, Nomad, MinIO).

### [INFRA-01] Monorepo & Docker Compose Scaffolding

- **Description**: Setup of the monorepo structure and the root `compose.yml`.
- **Tasks**:
    - Create folders: `apps/broker` (Java), `apps/frontend` (Next.js), `apps/agent` (for Sidecar image builds).
    - Create root `compose.yml` with:
        - `postgres` (Port 5432)
        - `redis` (Port 6379)
        - `minio` (Port 9000, Console 9001)
        - `nomad` (Server and Client mode)
- **Acceptance Criteria**: Running `docker-compose up` starts all infrastructure services without errors.

### [INFRA-02] Lease Enforcer Dockerfile

- **Description**: Repurpose the `apps/agent` directory to build the lightweight ephemeral sidecar.
- **Tasks**:
    - Base image: Alpine Linux or minimal Go binary.
    - Implement the core environment variable parsing (`JOB_ID`, `BROKER_API_URL`, `SECURE_TOKEN`).
- **Acceptance Criteria**: The image builds successfully and can be pushed to a local registry.

### [DB-01] Database Schema Migration

- **Description**: Automate schema application on Broker startup.
- **Tasks**:
    - Configure Flyway or Liquibase in `apps/broker`.
    - Place `db/init/01_schema.sql` into the migration path.
- **Acceptance Criteria**: `users`, `wallets`, `nodes`, `jobs` tables exist in Postgres upon startup.

### [BE-01] Backend Core Structure

- **Description**: Initialize Spring Boot application.
- **Tasks**:
    - Setup Dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Redis Reactive.
    - Configure Global Exception Handling (RFC 7807 Problem Details).
- **Acceptance Criteria**: `GET /actuator/health` returns UP.

---

## Milestone 2: Control Plane & User Dashboard (Weeks 3-4)

**Goal**: Users can log in, view their wallet, and the Broker automatically tracks physical nodes via Nomad.

### [BE-02] User & Wallet Domains

- **Description**: Implement Entities and Repositories.
- **Tasks**:
    - Map `User` and `Wallet` entities to DB tables.
    - Implement `AuthService` (Mock or JWT).
    - Implement `WalletService.getBalance(userId)`.
- **Acceptance Criteria**: Can create a user via SQL and retrieve their balance via API.

### [BE-03] Node Tracking via Nomad API

- **Description**: The Broker actively monitors Nomad for capacity instead of relying on host heartbeats.
- **Tasks**:
    - Implement a `@Scheduled` task in Spring Boot (every 30s).
    - Call Nomad API: `GET /v1/nodes`.
    - Upsert logic: Update PostgreSQL `nodes` table with `total_cpu_cores`, `total_ram_gb`, and `last_heartbeat`.
- **Acceptance Criteria**: The `nodes` table in Postgres accurately reflects the Nomad cluster state.

### [FE-01] Frontend Shell & Auth

- **Description**: Next.js setup with Authentication.
- **Tasks**:
    - Setup Next.js + Tailwind/MUI.
    - Create Login Page (Mock or real).
    - Create `AuthProvider` context.
- **Acceptance Criteria**: User can log in and see a protected "Dashboard" route.

### [FE-02] Dashboard Widgets

- **Description**: Visualize the system state.
- **Tasks**:
    - **Wallet Widget**: Fetch and display `GET /api/wallet`.
    - **Nodes Widget**: Fetch and display `GET /api/nodes` (list of active infrastructure).
- **Acceptance Criteria**: Dashboard shows real data from the DB.

---

## Milestone 3: The Lease Engine & Job Execution (Weeks 5-7)

**Goal**: End-to-end job submission using the dynamic 3-task Nomad group.

### [BE-04] Job Submission & Ledger

- **Description**: Handle the user's request to start work.
- **Tasks**:
    - `POST /api/jobs`:
        1. Validate Request.
        2. **Transaction**: `SELECT wallet FOR UPDATE`.
        3. Deduct *1st Lease* (15 mins cost).
        4. Insert `JOB` record.
        5. Insert `LEDGER` record (`LEASE_DEDUCTION`).
        6. Return Job ID.
- **Acceptance Criteria**: Wallet balance decreases immediately upon submission.

### [BE-05] Nomad Dispatch (The 3-Task Group)

- **Description**: Send the strictly formatted job to the Scheduler.
- **Tasks**:
    - Generate MinIO pre-signed URLs for the upload phase.
    - Construct the Nomad JSON payload with 3 tasks:
        1. `lease-enforcer` (lifecycle: sidecar, restart: fail).
        2. `user-workload` (main Docker container).
        3. `artifact-uploader` (lifecycle: poststop).
    - Call Nomad `POST /v1/jobs`.
- **Acceptance Criteria**: The combined task group successfully deploys via Nomad.

### [SIDECAR-01] Ephemeral Lease Enforcement Logic

- **Description**: Program the strict HTTP financial rules into the sidecar script.
- **Tasks**:
    - **Timer**: Initialize 15-minute countdown.
    - **Loop**: Call Broker `POST /api/internal/jobs/{id}/renew` every 10 mins.
    - **Rule 200 OK**: Reset timer to 15 mins.
    - **Rule 402 Required**: Execute `exit 1` immediately.
    - **Rule Timeout**: Let timer count down; execute `exit 1` if timer reaches 0.
- **Acceptance Criteria**: Sidecar triggers a Nomad allocation failure exactly when rules dictate.

### [BE-06] Lease Renewal API

- **Description**: Process lease extensions from the sidecar.
- **Tasks**:
    - `POST /api/internal/jobs/{id}/renew`:
        1. Lock Wallet.
        2. Deduct *Next Lease*.
        3. Update `jobs.active_lease_expires_at`.
        4. Insert `LEDGER` record.
- **Acceptance Criteria**: Wallet balance decreases periodically while job runs.

---

## Milestone 4: Artifacts, Refunds & Resilience (Weeks 8+)

**Goal**: Complete the lifecycle natively via Nomad hooks.

### [NOMAD-01] Artifact Upload Poststop Task

- **Description**: Save the user's work directly to MinIO.
- **Tasks**:
    - Configure the `artifact-uploader` task to mount the shared `alloc/data` directory.
    - On execution, zip the directory contents.
    - Execute a direct PUT request via `curl` to the pre-signed MinIO URL provided in its environment variables.
- **Acceptance Criteria**: Zip file appears in MinIO console automatically after the job finishes or is hard-killed.

### [BE-07] Lease Refunds & Reconciliation

- **Description**: Don't charge for unused time.
- **Tasks**:
    - Listen for Nomad allocation terminal events.
    - Calculate unused minutes of the final active lease.
    - Insert `LEDGER` record (`LEASE_REFUND`).
    - Credit Wallet.
- **Acceptance Criteria**: Finishing a job early automatically refunds the remaining fraction of the 15-minute chunk.

### [FE-03] Job History & Downloads

- **Description**: User can get their results.
- **Tasks**:
    - **Job List**: Show past jobs, status, and cost.
    - **Download**: Button to generate a Pre-signed Download URL from the Broker.
- **Acceptance Criteria**: User can download the zip results of a completed job.
