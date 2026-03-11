Private Cloud Resource Manager.

## Project Plan: Private Cloud Resource Manager

This document outlines the architecture, technology stack, and implementation milestones for a distributed on-premise
cloud system. The system provisions institutional hardware for batch jobs using Docker, orchestrated by Nomad, with a
strictly controlled Compute Unit (CU) billing system.

### 1. Technology Stack and Component Mapping

| Component                     | Technology                 | Primary Responsibility                                                                                                                                                  |
|-------------------------------|----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Frontend UI**               | React, Next.js, TypeScript | User dashboard, job submission (Docker image URL, resource sliders), CU wallet balance, and result downloads.                                                           |
| **Broker (Control Plane)**    | Java (Spring Boot)         | API Gateway, CU transaction validation, Redis locking, Nomad API communication, and lease issuance/billing updates.                                                     |
| **Database & State**          | PostgreSQL, Redis          | Postgres: Persistent state, user accounts, CU ledger with pessimistic locking. Redis: Distributed locks, real-time Agent lease renewals.                                |
| **Worker Node Service**       | Python (or Java)           | Your proprietary software installed directly on every computer sharing resources. Manages compute leases, monitors Docker stats, and enforces offline hard-kill limits. |
| **Orchestration & Execution** | HashiCorp Nomad, Docker    | Nomad client receives tasks from the Broker. Docker enforces cgroup limits (CPU/RAM/GPU) and isolates the student's process.                                            |
| **Artifact Storage**          | MinIO (S3-Compatible)      | Centralized, decoupled storage for output logs, trained models, and dataset retrieval.                                                                                  |

---

### 2. Core System Workflows

**A. Job Submission and Scheduling**

* The user submits a job via the Next.js UI, providing a Docker image URL, a run command, and hardware requirements.
* The Java Broker verifies the user's CU balance and deducts an initial 15-minute pre-paid compute lease using
  pessimistic locking (`SELECT ... FOR UPDATE`) in PostgreSQL to prevent concurrent overdrafts.
* To prevent the "Thundering Herd" problem, the Broker acquires a distributed lock in Redis for the target resource pool
  before proceeding.
* The Broker registers the job with the centralized Nomad Server via API, which dispatches it to an available worker
  node.

**B. Execution and Billing (The Proprietary Agent)**

* The Nomad client on the worker node pulls the image and starts the Docker container with strict resource limits.
* Your proprietary Agent, running locally on that exact same hardware, detects the running container.
* The Agent periodically contacts the Java Broker to renew its 15-minute compute leases, which deducts CUs in chunks
  upfront from the PostgreSQL ledger.
* If the network drops between the worker node and the Broker, the Agent allows the container to run only until the
  active 15-minute lease expires, after which it hard-kills the container to prevent unbilled compute overdrafts.

**C. Result Retrieval**

* Upon task completion (or OOM-kill/timeout), a shutdown script packages the designated output directory, and a refund
  transaction is issued to the ledger for any unused lease time.
* The package is uploaded directly to a pre-signed MinIO bucket URL.
* The container is destroyed, the resource lock is released, and the Next.js UI presents a download link to the user.

---

### 3. Development Milestones

**Milestone 1: Infrastructure and Database Foundation**

* Design the PostgreSQL schema for users, resources, and the append-only CU transaction ledger.
* Set up the Redis instance for distributed locking and lease state storage.
* Deploy a local MinIO instance and configure S3 buckets for result storage.

**Milestone 2: The Control Plane (Broker & UI)**

* Develop the Java Broker REST API for handling user authentication and basic job submission requests.
* Implement the transactional logic with pessimistic locking for issuing 15-minute leases and managing Redis locks.
* Build the Next.js frontend to allow users to log in, view their CU balance, and see available hardware nodes.

**Milestone 3: Orchestration and The Data Plane (Agent)**

* Set up a small Nomad cluster (1 server, 1-2 clients) and configure Docker on the client nodes.
* Develop the proprietary Agent to run on the worker nodes alongside Nomad.
* Implement the lease renewal mechanism between the Agent and the Broker.

**Milestone 4: Integration and Edge Case Handling**

* Connect the Next.js UI job submission to the Java Broker, which then triggers Nomad to spin up the Docker container.
* Implement the MinIO upload logic and unused lease refund logic at the end of the container lifecycle.
* Build and test the network partition logic, deliberately dropping the connection to ensure the Agent strictly monitors
  the lease boundary and hard-kills the container upon offline expiration.
