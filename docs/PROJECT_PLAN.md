Private Cloud Resource Manager.

## Project Plan: Private Cloud Resource Manager

This document outlines the architecture, technology stack, and implementation milestones for a distributed on-premise cloud system. The system provisions institutional hardware for batch jobs using Docker, orchestrated by Nomad, with a strictly controlled Compute Unit (CU) billing system.

### 1. Technology Stack and Component Mapping

| Component | Technology | Primary Responsibility |
| --- | --- | --- |
| **Frontend UI** | React, Next.js, TypeScript | User dashboard, job submission (Docker image URL, resource sliders), CU wallet balance, and result downloads. |
| **Broker (Control Plane)** | Java (Spring Boot) | API Gateway, CU transaction validation, Redis locking, Nomad API communication, and billing updates. |
| **Database & State** | PostgreSQL, Redis | Postgres: Persistent state, user accounts, CU ledger. Redis: Distributed locks, real-time Agent heartbeats. |
| **Worker Node Service** | Python (or Java) | Your proprietary software installed directly on every computer sharing resources. Tracks local execution time, monitors Docker stats, and handles network partition logic. |
| **Orchestration & Execution** | HashiCorp Nomad, Docker | Nomad client receives tasks from the Broker. Docker enforces cgroup limits (CPU/RAM/GPU) and isolates the student's process. |
| **Artifact Storage** | MinIO (S3-Compatible) | Centralized, decoupled storage for output logs, trained models, and dataset retrieval. |

---

### 2. Core System Workflows

**A. Job Submission and Scheduling**

* The user submits a job via the Next.js UI, providing a Docker image URL, a run command, and hardware requirements.
* The Java Broker verifies the user's CU balance in PostgreSQL.
* To prevent the "Thundering Herd" problem, the Broker acquires a distributed lock in Redis for the target resource pool before proceeding.
* The Broker registers the job with the centralized Nomad Server via API, which dispatches it to an available worker node.

**B. Execution and Billing (The Proprietary Agent)**

* The Nomad client on the worker node pulls the image and starts the Docker container with strict resource limits.
* Your proprietary Agent, running locally on that exact same hardware, detects the running container.
* The Agent streams 5-minute billing heartbeats back to the Java Broker to deduct CUs from the PostgreSQL ledger.
* If the network drops between the worker node and the Broker, the Agent locally caches the elapsed execution time and synchronizes the eventual CU deduction once the connection is restored.

**C. Result Retrieval**

* Upon task completion (or OOM-kill/timeout), a shutdown script packages the designated output directory.
* The package is uploaded directly to a pre-signed MinIO bucket URL.
* The container is destroyed, the resource lock is released, and the Next.js UI presents a download link to the user.

---

### 3. Development Milestones

**Milestone 1: Infrastructure and Database Foundation**

* Design the PostgreSQL schema for users, resources, and the CU transaction ledger.
* Set up the Redis instance for distributed locking and heartbeat storage.
* Deploy a local MinIO instance and configure S3 buckets for result storage.

**Milestone 2: The Control Plane (Broker & UI)**

* Develop the Java Broker REST API for handling user authentication and basic job submission requests.
* Implement the transactional logic for deducting CUs and managing Redis locks.
* Build the Next.js frontend to allow users to log in, view their CU balance, and see available hardware nodes.

**Milestone 3: Orchestration and The Data Plane (Agent)**

* Set up a small Nomad cluster (1 server, 1-2 clients) and configure Docker on the client nodes.
* Develop the proprietary Agent to run on the worker nodes alongside Nomad.
* Implement the heartbeat mechanism between the Agent and the Broker.

**Milestone 4: Integration and Edge Case Handling**

* Connect the Next.js UI job submission to the Java Broker, which then triggers Nomad to spin up the Docker container.
* Implement the MinIO upload logic at the end of the container lifecycle.
* Build and test the network partition logic, deliberately dropping the connection to ensure the Agent locally tracks and later syncs CU usage without double-billing.
