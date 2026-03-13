# Development Roadmap

This document outlines the step-by-step implementation plan for the Private Cloud Resource Manager (PCRM). It is designed for a **4-person team** working in a **Monorepo** structure.

The strategy relies on a **"Virtual Node"** approach for development:
- **Broker (Control Plane)**: The central API.
- **Agent (Data Plane)**: A containerized service that simulates a physical lab machine. It mounts the host's Docker socket to spawn sibling containers, mimicking a real node's behavior.
- **Frontend**: The user dashboard.

---

## Milestone 1: Monorepo & Virtual Infrastructure (Weeks 1-2)
**Goal**: A single command `docker-compose up` spins up the entire simulated cloud (Database, Broker, Agent, Nomad).

### [INFRA-01] Monorepo & Docker Compose Scaffolding
- **Description**: extensive setup of the monorepo structure and the root `compose.yml`.
- **Tasks**:
  - Create folders: `apps/broker` (Java), `apps/frontend` (Next.js), `apps/agent` (Python).
  - Create root `compose.yml` with:
    - `postgres` (Port 5432)
    - `redis` (Port 6379)
    - `minio` (Port 9000, Console 9001)
    - `nomad` (Server mode)
- **Acceptance Criteria**: Running `docker-compose up` starts all infrastructure services without errors.

### [INFRA-02] Virtual Node (Agent) Dockerfile
- **Description**: Create a Dockerfile for `apps/agent` that can act as a "Virtual Node".
- **Tasks**:
  - Base image: Python 3.11-slim (or Java if preferred).
  - Install `docker` CLI inside the image.
  - Configure `compose.yml` to mount `/var/run/docker.sock:/var/run/docker.sock` so the Agent can spawn sibling containers (simulating job execution).
- **Acceptance Criteria**: The Agent container can run `docker ps` and see the host's containers.

### [DB-01] Database Schema Migration
- **Description**: Automate schema application on Broker startup.
- **Tasks**:
  - Configure Flyway or Liquibase in `apps/broker`.
  - Place `db/init/01_schema.sql` into the migration path.
- **Acceptance Criteria**: `users`, `wallets`, `nodes`, `jobs` tables exist in Postgres upon startup.

### [BE-01] Backend Core Structure
- **Description**: Initialize Spring Boot application.
- **Tasks**:
  - Setup Dependencies: Spring Web, Spring Data JPA, PosteSQL Driver, Redis Reactive.
  - Configure Global Exception Handling (RFC 7807 Problem Details).
  - Configure Swagger/OpenAPI.
- **Acceptance Criteria**: `GET /actuator/health` returns UP.

---

## Milestone 2: Control Plane & User Dashboard (Weeks 3-4)
**Goal**: Users can log in, view their wallet, and see "Virtual Nodes" automatically registering themselves.

### [BE-02] User & Wallet Domains
- **Description**: Implement Entities and Repositories.
- **Tasks**:
  - Map `User` and `Wallet` entities to DB tables.
  - Implement `AuthService` (Mock or JWT).
  - Implement `WalletService.getBalance(userId)`.
- **Acceptance Criteria**: Can create a user via SQL and retrieve their balance via API.

### [AGENT-01] Heartbeat & Registration Loop
- **Description**: The Agent needs to announce its existence to the Broker.
- **Tasks**:
  - Implement a loop (every 30s) in `apps/agent`.
  - Collect mock stats: CPU (available), RAM (available).
  - Send `POST /api/internal/nodes/heartbeat`.
- **Acceptance Criteria**: The `nodes` table in Postgres is populated with the Agent's hostname.

### [BE-03] Node Registration API
- **Description**: API to handle Agent heartbeats.
- **Tasks**:
  - Create `POST /api/internal/nodes/heartbeat`.
  - Upsert logic: If node exists (by hostname), update `last_heartbeat`. If not, `INSERT`.
- **Acceptance Criteria**: Agent logs show "Heartbeat success: 200 OK".

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
  - **Nodes Widget**: Fetch and display `GET /api/nodes` (list of active agents).
- **Acceptance Criteria**: Dashboard shows real data from the DB.

---

## Milestone 3: The Lease Engine & Job Execution (Weeks 5-7)
**Goal**: End-to-end job submission. The Agent enforces the "No Unbilled Compute" rule.

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

### [BE-05] Nomad Dispatch
- **Description**: Send the job to the Scheduler.
- **Tasks**:
  - Integrate Nomad Java Client (or HTTP calls).
  - In `JobService`, after successful DB transaction, call Nomad API to run the Docker task.
- **Acceptance Criteria**: A new container appears on the "Virtual Node" (Agent).

### [AGENT-02] Lease Monitor & Renewal
- **Description**: The core financial enforcement loop.
- **Tasks**:
  - **Detection**: Watch for containers with a specific label (e.g., `pcrm_job_id`).
  - **Renewal**: Every 10 minutes, call `POST /api/internal/jobs/{id}/renew`.
  - **Hard-Kill**: If renewal fails (network/balance) and `active_lease_expires_at` is passed, execute `docker kill`.
- **Acceptance Criteria**:
  - Normal case: Job runs for >15 mins, multiple ledger deductions occur.
  - Failure case: Stop Broker. Agent kills container after lease expiry.

### [BE-06] Lease Renewal API
- **Description**: Process lease extensions.
- **Tasks**:
  - `POST /api/internal/jobs/{id}/renew`:
    1. Lock Wallet.
    2. Deduct *Next Lease*.
    3. Update `jobs.active_lease_expires_at`.
    4. Insert `LEDGER` record.
- **Acceptance Criteria**: Wallet balance decreases periodically while job runs.

---

## Milestone 4: Artifacts, Refunds & Resilience (Weeks 8+)
**Goal**: Complete the lifecycle. Handle early exits and data retrieval.

### [BE-07] Lease Refunds
- **Description**: Don't charge for unused time.
- **Tasks**:
  - When job finishes (Agent reports status `COMPLETED`), calculate unused minutes of the current lease.
  - Insert `LEDGER` record (`LEASE_REFUND`).
  - Credit Wallet.
- **Acceptance Criteria**: Finishing a job 1 minute into a 15-minute lease refunds ~14 minutes worth of credits.

### [INFRA-03] MinIO Storage
- **Description**: Object storage for logs/outputs.
- **Tasks**:
  - Configure MinIO buckets: `job-artifacts`.
  - BE: Generate Pre-signed Upload URL (for Agent).
  - BE: Generate Pre-signed Download URL (for User).
- **Acceptance Criteria**: Can upload a file via `curl` using the generated URL.

### [AGENT-03] Artifact Upload
- **Description**: Save the user's work.
- **Tasks**:
  - On container exit, zip the mounted output directory.
  - Request Upload URL from Broker.
  - PUT zip file to MinIO.
- **Acceptance Criteria**: Zip file appears in MinIO console after job finishes.

### [FE-03] Job History & Downloads
- **Description**: User can get their results.
- **Tasks**:
  - **Job List**: Show past jobs, status, and cost.
  - **Download**: Button to trigger the Download URL generation.
- **Acceptance Criteria**: User can download the results of a completed job.

### [SYS-01] Chaos Testing (Network Partition)
- **Description**: Verify the "Hard-Kill" safeguard.
- **Tasks**:
  - Add a "Simulate Network Failure" switch to the Agent (block traffic to Broker).
  - Run a job. Activate switch.
  - Verify Agent kills container exactly when lease expires.
- **Acceptance Criteria**: The system does not allow free compute during network outages.
