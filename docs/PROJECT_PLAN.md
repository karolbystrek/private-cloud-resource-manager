Private Cloud Resource Manager.

## Project Plan: Private Cloud Resource Manager

> **Development Roadmap**: For the detailed task list and GitHub-style issues, please see [docs/ROADMAP.md](ROADMAP.md).

This document outlines the architecture, technology stack, and implementation milestones for a distributed on-premise
cloud system. The system provisions institutional hardware for batch jobs using Docker, orchestrated by Nomad, with a
strictly controlled credits billing system.

### 1. Technology Stack and Component Mapping

| Component                     | Technology                 | Primary Responsibility                                                                                                                     |
|-------------------------------|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| **Frontend UI**               | React, Next.js, TypeScript | User dashboard, job submission (Docker image URL, resource sliders), credits wallet balance, and result downloads.                         |
| **Broker (Control Plane)**    | Java (Spring Boot)         | API Gateway, credits transaction validation, Redis locking, Nomad API communication, node polling, and lease issuance/billing updates.     |
| **Database & State**          | PostgreSQL, Redis          | Postgres: Persistent state, user accounts, credit registry with pessimistic locking. Redis: Distributed locks for resource allocation.      |
| **Lease Enforcer (Sidecar)**  | Lightweight Container      | An ephemeral sidecar container dynamically injected into every job to manage compute leases and strictly enforce offline hard-kill limits. |
| **Orchestration & Execution** | HashiCorp Nomad, Docker    | Nomad schedules tasks dynamically. Docker enforces cgroup limits (CPU/RAM/GPU) and isolates the student's process.                         |
| **Artifact Storage**          | MinIO (S3-Compatible)      | Centralized, decoupled storage for output logs, trained models, and dataset retrieval.                                                     |

---

### 2. Core System Workflows

**A. Infrastructure Tracking**

* The Java Broker periodically polls the central Nomad Server API (`GET /v1/nodes`) to discover available physical
  laboratory machines.
* The Broker updates the Postgres database with the latest node availability, CPU, and RAM capacity, acting as the
  single source of truth without requiring custom daemons on the physical hardware.

**B. Job Submission and Scheduling**

* The user submits a job via the Next.js UI, providing a Docker image URL, a run command, and hardware requirements.
* The Java Broker verifies the user's credits balance and deducts an initial 15-minute pre-paid compute lease using
  pessimistic locking (`SELECT ... FOR UPDATE`) in PostgreSQL.
* The Broker constructs a specialized 3-task Nomad group consisting of:
    1. The `lease-enforcer` sidecar (prestart).
    2. The `user-workload` container.
    3. The `artifact-uploader` hook (poststop).
* The Broker dispatches this job group to the Nomad API.

**C. Execution and Billing (Dynamic Enforcer)**

* Nomad schedules the task group onto an available worker node.
* The `lease-enforcer` sidecar runs an internal 15-minute countdown timer. Every 10 minutes, it contacts the Broker to
  renew the lease.
* **HTTP 200**: The Broker deducts another 15 minutes of credits from the credit registry. The sidecar resets its internal timer.
* **HTTP 402**: The user is out of funds. The sidecar immediately exits with code 1.
* **Network Partition**: The sidecar cannot reach the Broker. The countdown continues. If it reaches zero without a
  successful renewal, the sidecar exits with code 1.
* If the sidecar exits with an error code, Nomad marks the allocation as failed and natively hard-kills the
  `user-workload` container, strictly preventing unbilled compute.

**D. Result Retrieval**

* Upon task completion, OOM-kill, or a hard-kill enforcement, Nomad triggers the `artifact-uploader` poststop container.
* This container zips the shared `alloc/data` directory and executes a direct PUT request to a Broker-generated MinIO
  pre-signed URL.
* The Nomad allocation terminates, the Broker issues a refund transaction to the credit registry for any unused lease time, and
  the UI presents a download link.

---

### 3. Development Milestones

**Milestone 1: Infrastructure and Database Foundation**

* Design the PostgreSQL schema for users, resources, and the append-only credit transaction registry.
* Set up the Redis instance for distributed locking and lease state storage.
* Deploy a local MinIO instance and configure S3 buckets for result storage.
* Build the ephemeral `lease-enforcer` sidecar Docker image.

**Milestone 2: The Control Plane (Broker & UI)**

* Develop the Java Broker REST API for handling user authentication and basic job submission requests.
* Implement the background Nomad polling mechanism for automated node tracking.
* Build the Next.js frontend to allow users to log in, view their credits balance, and see available hardware nodes.

**Milestone 3: Orchestration and The Data Plane (Sidecar)**

* Set up a small Nomad cluster (1 server, 1-2 clients) and configure Docker on the client nodes.
* Implement the transactional logic with pessimistic locking for issuing 15-minute leases.
* Build the Nomad job dispatch integration in the Broker to dynamically inject the 3-task enforcement group.

**Milestone 4: Integration and Edge Case Handling**

* Connect the Next.js UI job submission to the Java Broker, triggering Nomad scheduling.
* Implement the MinIO upload logic via the Nomad poststop task and the unused lease refund logic during Broker
  reconciliation.
* Conduct chaos testing by deliberately dropping the Sidecar-to-Broker network connection to ensure the hard-kill
  mechanism functions natively via Nomad upon countdown expiration.
