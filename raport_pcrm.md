# Private Cloud Resource Manager — Raport Architektoniczny

> **Repozytorium:** `karolbystrek/private-cloud-resource-manager`
> **Data analizy:** 18 czerwca 2026
> **Wersja Spring Boot:** 4.0.3 · **Java:** 25 · **Next.js:** 16.x · **Nomad:** 1.7

---

## Spis treści

1. [Wprowadzenie i cel projektu](#1-wprowadzenie-i-cel-projektu)
2. [Architektura wysokopoziomowa](#2-architektura-wysokopoziomowa)
3. [Stos technologiczny](#3-stos-technologiczny)
4. [Infrastruktura Docker Compose](#4-infrastruktura-docker-compose)
5. [Backend — struktura pakietów](#5-backend--struktura-pakietów)
6. [Model danych (ERD)](#6-model-danych-erd)
7. [Uwierzytelnianie i autoryzacja](#7-uwierzytelnianie-i-autoryzacja)
8. [Cykl życia zadania (Job Lifecycle)](#8-cykl-życia-zadania-job-lifecycle)
9. [System Outbox — asynchroniczne zdarzenia](#9-system-outbox--asynchroniczne-zdarzenia)
10. [Mechanizm leasingowy (Prepaid Billing)](#10-mechanizm-leasingowy-prepaid-billing)
11. [System kwotowy](#11-system-kwotowy)
12. [Synchronizacja węzłów Nomad](#12-synchronizacja-węzłów-nomad)
13. [Dyspozycja zadań do Nomad](#13-dyspozycja-zadań-do-nomad)
14. [Przechowywanie artefaktów (S3/MinIO)](#14-przechowywanie-artefaktów-s3minio)
15. [Przechwytywanie logów](#15-przechwytywanie-logów)
16. [API REST — mapa endpointów](#16-api-rest--mapa-endpointów)
17. [Frontend — architektura i routing](#17-frontend--architektura-i-routing)
18. [Background Workers i Schedulery](#18-background-workers-i-schedulery)
19. [Idempotencja](#19-idempotencja)
20. [Podsumowanie](#20-podsumowanie)

---

## 1. Wprowadzenie i cel projektu

**Private Cloud Resource Manager (PCRM)** to platforma do zarządzania zasobami obliczeniowymi w prywatnej chmurze. System umożliwia użytkownikom przesyłanie zadań obliczeniowych (batch jobs), które są wykonywane na klastrze zarządzanym przez HashiCorp Nomad. Kluczowe cechy:

- **Prepaid lease billing** — użytkownicy mają miesięczny limit minut obliczeniowych. Przed uruchomieniem zadania rezerwowane są minuty z kwoty.
- **Batch job execution** — zadania są konteneryzowane (Docker), kolejkowane FIFO i dispatczowane do Nomad.
- **Self-hosted identity** — uwierzytelnianie opiera się na self-hosted Supabase (GoTrue + Kong).
- **Artifact management** — wyniki zadań (artefakty) są automatycznie pakowane i przesyłane do MinIO (S3-compatible).
- **Role-based quotas** — trzy role (STUDENT, EMPLOYEE, ADMIN) z konfigurowalnymi politykami kwotowymi.

---

## 2. Architektura wysokopoziomowa

```mermaid
graph TB
    subgraph "Klient"
        Browser["🌐 Przeglądarka"]
    end

    subgraph "Frontend (Next.js 16)"
        FE["Frontend<br/>:3000"]
    end

    subgraph "API Gateway (Kong)"
        Kong["Kong<br/>:8000 / :8443"]
    end

    subgraph "Supabase Auth Stack"
        Auth["GoTrue<br/>(Auth)"]
        Studio["Supabase Studio"]
        Meta["PG Meta"]
        Analytics["Logflare<br/>(Analytics)"]
    end

    subgraph "Control Plane (Spring Boot 4)"
        Backend["Backend API<br/>:8080/api"]
    end

    subgraph "Execution Plane"
        Nomad["HashiCorp Nomad<br/>:4646"]
        DockerD["Docker Engine"]
    end

    subgraph "Data Layer"
        DB[("PostgreSQL 15<br/>(Supabase)")]
        MinIO["MinIO S3<br/>:9000"]
    end

    Browser --> FE
    FE --> Backend
    Browser --> Kong
    Kong --> Auth
    Kong --> Studio
    Kong --> Meta
    Backend --> DB
    Backend --> Nomad
    Backend --> MinIO
    Auth --> DB
    Analytics --> DB
    Meta --> DB
    Nomad --> DockerD
    DockerD -->|"artifact upload"| MinIO

    style Backend fill:#4f46e5,color:#fff
    style Nomad fill:#00bc7e,color:#fff
    style DB fill:#3b82f6,color:#fff
    style MinIO fill:#c62828,color:#fff
    style Kong fill:#003459,color:#fff
    style FE fill:#0f172a,color:#fff
```

---

## 3. Stos technologiczny

| Warstwa | Technologia | Wersja |
|---------|------------|--------|
| **Backend** | Spring Boot (WebMVC, JPA, Security, Flyway) | 4.0.3 |
| **Język** | Java | 25 |
| **Frontend** | Next.js + React + TypeScript + Tailwind CSS + shadcn/ui | 16.x / React 19 |
| **Baza danych** | PostgreSQL (Supabase) | 15.8 |
| **Orkiestracja** | HashiCorp Nomad | 1.7 |
| **Object Storage** | MinIO (S3-compatible) | latest |
| **Tożsamość** | Supabase GoTrue (JWT HS256) | v2.186 |
| **API Gateway** | Kong | 3.9.1 |
| **ORM** | Hibernate (JPA) | — |
| **Migracje** | Flyway | — |
| **Build** | Maven (backend), npm (frontend) | — |
| **Konteneryzacja** | Docker + Docker Compose | — |
| **API Docs** | SpringDoc OpenAPI (swagger-ui) | 3.0.1 |
| **S3 SDK** | AWS SDK for Java v2 | 2.30.37 |

---

## 4. Infrastruktura Docker Compose

System składa się z **10 serwisów** zdefiniowanych w jednym pliku `docker-compose.yml`:

```mermaid
graph LR
    subgraph "Supabase Stack"
        DB["supabase-db<br/>PostgreSQL :5432"]
        Auth["supabase-auth<br/>GoTrue :9999"]
        Kong["supabase-kong<br/>:8000/:8443"]
        Studio["supabase-studio<br/>:3000"]
        Meta["supabase-meta<br/>:8080"]
        Analytics["supabase-analytics<br/>Logflare :4000"]
    end

    subgraph "Object Storage"
        MinIO["minio<br/>:9000/:9001"]
        MinIOInit["minio-init<br/>(bucket setup)"]
    end

    subgraph "PCRM Application"
        Backend["backend<br/>Spring Boot :8080"]
        Frontend["frontend<br/>Next.js :3000"]
        Nomad["nomad<br/>:4646/:4647/:4648"]
    end

    DB --> Auth
    DB --> Meta
    DB --> Analytics
    Auth --> Kong
    Studio --> Kong
    Studio --> Analytics
    MinIO --> MinIOInit
    DB --> Backend
    MinIOInit --> Backend
    Kong --> Backend
    Backend --> Frontend

    style Backend fill:#4f46e5,color:#fff
    style Frontend fill:#0f172a,color:#fff
    style Nomad fill:#00bc7e,color:#fff
```

### Zależności startowe serwisów

| Serwis | Zależy od | Warunek |
|--------|-----------|---------|
| `auth` | `db` | `service_healthy` |
| `studio` | `analytics` | `service_healthy` |
| `kong` | `studio` | `service_healthy` |
| `meta` | `db` | `service_healthy` |
| `analytics` | `db` | `service_healthy` |
| `minio-init` | `minio` | `service_healthy` |
| `backend` | `db`, `minio-init`, `kong` | `healthy` / `completed_successfully` |
| `frontend` | `backend` | `service_healthy` |

---

## 5. Backend — struktura pakietów

```mermaid
graph TD
    Root["com.pcrm.backend"]
    Root --> Auth["auth<br/>• config (SecurityConfig, JwtDecoder)<br/>• domain (CustomUserDetails)<br/>• service"]
    Root --> Config["config<br/>• JacksonConfig<br/>• SchedulingConfig"]
    Root --> Events["events<br/>• domain (OutboxMessage)<br/>• repository<br/>• service (OutboxPoller,<br/>  OutboxWriter, HandlerRegistry)"]
    Root --> Exception["exception<br/>• GlobalExceptionHandler<br/>• InsufficientQuotaException<br/>• NomadDispatchException<br/>• ResourceNotFoundException"]
    Root --> Idempotency["idempotency<br/>• domain (IdempotencyRecord)<br/>• service (IdempotencyService)"]
    Root --> Jobs["jobs<br/>• domain (Job, JobStatus,<br/>  JobLogStream, JobLogChunk)<br/>• dto (6 records)<br/>• repository (3 repos)<br/>• resource (JobsResource)<br/>• service (17 klas)<br/>• validation"]
    Root --> Nodes["nodes<br/>• domain (Node, NodeStatus)<br/>• dto<br/>• repository<br/>• resource (NodesResource)<br/>• service (NodeQueryService,<br/>  NodeSyncService)"]
    Root --> NomadPkg["nomad<br/>• interfaces (4 clients)<br/>• http (4 impl.)<br/>• config<br/>• stream"]
    Root --> Quota["quota<br/>• domain (10 encji)<br/>• dto<br/>• repository<br/>• resource (QuotaResource,<br/>  AdminQuotaResource)<br/>• service (QuotaAccountingService,<br/>  QuotaPolicyResolverService,<br/>  AdminQuotaGrantService)"]
    Root --> Storage["storage<br/>• domain (JobArtifact,<br/>  JobArtifactStatus)<br/>• dto<br/>• repository<br/>• resource (StorageResource,<br/>  InternalStorageResource)<br/>• service (StorageService,<br/>  JobArtifactService)"]
    Root --> User["user<br/>• Profile<br/>• UserRole<br/>• repository"]

    style Jobs fill:#4f46e5,color:#fff
    style Quota fill:#059669,color:#fff
    style NomadPkg fill:#00bc7e,color:#fff
    style Storage fill:#c62828,color:#fff
```

---

## 6. Model danych (ERD)

```mermaid
erDiagram
    profiles {
        uuid id PK
        varchar role "STUDENT | EMPLOYEE | ADMIN"
        timestamp created_at
        timestamp updated_at
    }

    jobs {
        uuid id PK
        uuid profile_id FK
        varchar status "11 stanów"
        varchar docker_image
        varchar execution_command
        int req_cpu_cores
        int req_ram_gb
        boolean req_gpu
        long total_consumed_minutes
        json env_vars_json
        timestamp queued_at
        timestamp dispatch_requested_at
        timestamp dispatched_at
        timestamp started_at
        timestamp process_finished_at
        timestamp finalized_at
        timestamp active_lease_expires_at
        long current_lease_reserved_minutes
        long lease_sequence
        boolean lease_settled
        varchar terminal_reason
    }

    nodes {
        varchar id PK
        uuid nomad_node_id
        varchar hostname
        inet ip_address
        varchar status "AVAILABLE | OFFLINE"
        varchar nomad_status
        varchar scheduling_eligibility
        int total_cpu_cores
        int total_ram_mb
        boolean has_nvidia_gpu
        timestamp last_heartbeat
    }

    quota_policies {
        uuid id PK
        varchar role
        long monthly_minutes
        int role_weight
        boolean unlimited
        timestamp active_from
    }

    user_quota_overrides {
        uuid id PK
        uuid profile_id FK
        long monthly_minutes
        int role_weight
        boolean unlimited
        timestamp active_from
        timestamp expires_at
    }

    quota_grants {
        uuid id PK
        uuid profile_id FK
        timestamp interval_start
        timestamp interval_end
        varchar grant_type "ROLE_GRANT | ADMIN_BONUS"
        long minutes
        long remaining_minutes
        varchar status "ACTIVE | EXHAUSTED"
    }

    quota_reservations {
        uuid id PK
        uuid profile_id FK
        uuid job_id FK
        long reserved_compute_minutes
        long consumed_compute_minutes
        long released_compute_minutes
        varchar status "ACTIVE | CONSUMED | RELEASED"
    }

    user_quota_balance_current {
        uuid id PK
        uuid profile_id FK
        timestamp interval_start
        timestamp interval_end
        long granted_minutes
        long reserved_minutes
        long consumed_minutes
        long available_minutes
        long version
    }

    quota_usage_ledger {
        uuid id PK
        uuid profile_id FK
        uuid job_id FK
        uuid quota_reservation_id FK
        varchar entry_type "USAGE_DEBITED | USAGE_RELEASED"
        long compute_minutes
        int multiplier
        varchar reason_code
    }

    outbox_messages {
        uuid id PK
        varchar topic
        json payload
        json headers
        varchar status
        timestamp claimed_at
        varchar claimed_by
    }

    idempotency_records {
        uuid id PK
        varchar actor_type
        varchar actor_id
        varchar workflow
        varchar idempotency_key
        varchar status "STARTED | COMPLETED"
        json response_body
    }

    job_artifacts {
        uuid id PK
        uuid job_id FK
        uuid profile_id FK
        varchar object_key
        varchar status "PENDING | UPLOADING | AVAILABLE | MISSING"
        long size_bytes
        varchar checksum_sha256
    }

    job_log_streams {
        uuid id PK
        uuid job_id FK
        uuid profile_id FK
        varchar stream "stdout | stderr"
        long last_offset
        boolean capture_complete
    }

    job_log_chunks {
        uuid id PK
        uuid log_stream_id FK
        uuid job_id FK
        varchar stream
        long sequence
        varchar object_key
        long offset_start
        long offset_end
        long size_bytes
    }

    profiles ||--o{ jobs : "has"
    profiles ||--o{ quota_grants : "receives"
    profiles ||--o{ quota_reservations : "has"
    profiles ||--o{ user_quota_balance_current : "has"
    profiles ||--o{ user_quota_overrides : "may have"
    profiles ||--o{ job_artifacts : "owns"
    jobs ||--o| quota_reservations : "reserves"
    jobs ||--o| job_artifacts : "produces"
    jobs ||--o{ job_log_streams : "has"
    job_log_streams ||--o{ job_log_chunks : "contains"
    jobs ||--o{ quota_usage_ledger : "generates"
```

---

## 7. Uwierzytelnianie i autoryzacja

```mermaid
sequenceDiagram
    participant Browser
    participant FrontendProxy as Frontend<br/>(Middleware/Proxy)
    participant Supabase as Supabase GoTrue<br/>(via Kong)
    participant Backend as Backend API

    Browser->>FrontendProxy: GET /login
    FrontendProxy->>Browser: Formularz logowania

    Browser->>Supabase: POST /auth/v1/token<br/>(email + hasło)
    Supabase-->>Browser: JWT (access_token) +<br/>refresh_token

    Note over Browser: Token przechowywany<br/>w cookies (httpOnly)

    Browser->>FrontendProxy: GET /jobs
    FrontendProxy->>FrontendProxy: Sprawdź cookie<br/>(access_token / refresh_token)
    alt Brak tokena
        FrontendProxy-->>Browser: Redirect → /login
    else Tylko refresh_token
        FrontendProxy-->>Browser: Redirect → /api/auth/refresh
    else access_token present
        FrontendProxy->>Backend: GET /api/jobs<br/>Authorization: Bearer JWT
    end

    Backend->>Backend: SecurityConfig:<br/>OAuth2 Resource Server<br/>JWT → SupabaseJwtAuthConverter<br/>→ CustomUserDetails
    Backend->>Backend: Sprawdź rolę<br/>(@PreAuthorize)
    Backend-->>FrontendProxy: JSON response
    FrontendProxy-->>Browser: Rendered page
```

### Role i uprawnienia

| Rola | Opis | Dostęp |
|------|------|--------|
| `STUDENT` | Domyślna rola nowego użytkownika | Zadania własne, kwota, artefakty |
| `EMPLOYEE` | Pracownik z większą kwotą | Jak STUDENT + rozszerzona kwota |
| `ADMIN` | Administrator systemu | Pełny dostęp: węzły, polityki kwotowe, granty, admin panel |

> [!NOTE]
> Konfiguracja bezpieczeństwa w [SecurityConfig.java](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/auth/config/SecurityConfig.java) ustawia sesje jako **STATELESS**, a każdy request (poza healthcheck i swagger) wymaga uwierzytelnienia JWT.

---

## 8. Cykl życia zadania (Job Lifecycle)

### 8.1 Maszyna stanów

Klasa [JobStateMachine](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/JobStateMachine.java) zarządza przejściami między **11 stanami**:

```mermaid
stateDiagram-v2
    [*] --> SUBMITTED : submitJob()

    SUBMITTED --> QUEUED : admitWithQuotaReservation()
    SUBMITTED --> FAILED : rejectForInsufficientQuota()

    QUEUED --> DISPATCHING : markDispatchRequested()
    QUEUED --> CANCELED : markCanceledByUser()
    QUEUED --> TIMED_OUT : markTimedOut() [lease expired]

    DISPATCHING --> SCHEDULING : markDispatchAccepted()
    DISPATCHING --> DISPATCHING : markDispatchRetryRequested() [stale retry]
    DISPATCHING --> INFRA_FAILED : markDispatchFailed()
    DISPATCHING --> TIMED_OUT : markTimedOut()
    DISPATCHING --> CANCELED : markCanceledByUser()

    SCHEDULING --> RUNNING : applyNomadTransition()
    SCHEDULING --> FAILED : applyNomadTransition()
    SCHEDULING --> TIMED_OUT : markTimedOut()
    SCHEDULING --> CANCELED : markNomadStopped() / markCanceledByUser()

    RUNNING --> FINALIZING : applyNomadTransition() [process complete]
    RUNNING --> FAILED : applyNomadTransition()
    RUNNING --> TIMED_OUT : markTimedOut()
    RUNNING --> CANCELED : markNomadStopped() / markCanceledByUser()

    FINALIZING --> SUCCEEDED : markArtifactAvailable() / markFinalizedWithoutArtifact()
    FINALIZING --> FAILED : markArtifactFailed()

    SUCCEEDED --> [*]
    FAILED --> [*]
    CANCELED --> [*]
    TIMED_OUT --> [*]
    INFRA_FAILED --> [*]

    note right of SUCCEEDED : Stany terminalne:<br/>SUCCEEDED, FAILED,<br/>CANCELED, TIMED_OUT,<br/>INFRA_FAILED
```

### 8.2 Pełny przepływ zadania (sekwencja)

```mermaid
sequenceDiagram
    participant User
    participant JobsResource
    participant SubmissionSvc as JobSubmission<br/>Service
    participant IdempSvc as Idempotency<br/>Service
    participant OutboxWriter
    participant OutboxPoller
    participant AdmissionWorker as JobAdmission<br/>Worker
    participant QuotaSvc as QuotaAccounting<br/>Service
    participant StateMachine as JobState<br/>Machine
    participant Dispatcher as FifoJob<br/>Dispatcher
    participant NomadClient as Nomad<br/>HTTP Client
    participant Nomad

    User->>JobsResource: POST /api/jobs<br/>{dockerImage, command, cpuCores, ramGb}

    JobsResource->>SubmissionSvc: submitJob(userId, request, idempotencyKey)
    SubmissionSvc->>IdempSvc: execute(IdempotentWorkflow)
    IdempSvc->>IdempSvc: Sprawdź/utwórz<br/>IdempotencyRecord
    IdempSvc->>SubmissionSvc: prepareSubmission()
    SubmissionSvc->>SubmissionSvc: Zapisz Job (SUBMITTED)
    SubmissionSvc->>OutboxWriter: publish(JOB_SUBMITTED, {jobId})
    SubmissionSvc-->>JobsResource: PreparedJobSubmission
    JobsResource-->>User: 202 Accepted {jobId}

    Note over OutboxPoller: Scheduled poll (1s interval)
    OutboxPoller->>OutboxPoller: claimAvailable()
    OutboxPoller->>AdmissionWorker: handle(OutboxMessage)
    
    AdmissionWorker->>QuotaSvc: reserveInitialLease(profileId, job)
    QuotaSvc->>QuotaSvc: Sprawdź dostępną kwotę
    alt Wystarczająca kwota
        QuotaSvc->>QuotaSvc: Utwórz QuotaReservation<br/>Zarezerwuj minuty w UserQuotaBalance
        QuotaSvc-->>AdmissionWorker: reservedMinutes (np. 15)
        AdmissionWorker->>StateMachine: markQueued(job, now, 15)
        AdmissionWorker->>OutboxWriter: publish(JOB_QUEUED, {jobId})
    else Brak kwoty
        AdmissionWorker->>StateMachine: markRejectedForInsufficientQuota()
    end

    Note over Dispatcher: Triggered by JOB_QUEUED outbox<br/>or scheduled poll (3s)
    Dispatcher->>Dispatcher: loadClusterCapacity()
    Dispatcher->>Dispatcher: claimNextDispatchableJob()<br/>(FIFO, SELECT FOR UPDATE)
    Dispatcher->>StateMachine: markDispatchRequested()
    Dispatcher->>NomadClient: dispatchJob(NomadDispatchRequest)
    NomadClient->>Nomad: POST /v1/jobs (HCL template)
    Nomad-->>NomadClient: {nomadJobId, evalId}
    NomadClient-->>Dispatcher: NomadDispatchResult
    Dispatcher->>StateMachine: markDispatchAccepted()

    Note over Nomad: Nomad schedules<br/>& runs Docker container

    Nomad->>Nomad: Task "user-workload" runs
    Nomad->>Nomad: Task "artifact-uploader"<br/>(poststop lifecycle hook)<br/>→ ZIP & upload to MinIO
```

---

## 9. System Outbox — asynchroniczne zdarzenia

System implementuje **Transactional Outbox Pattern** do niezawodnej propagacji zdarzeń wewnętrznych.

```mermaid
graph TD
    subgraph "Producer"
        A["OutboxWriter<br/>.publish(topic, payload)"] -->|"INSERT"| B[("outbox_messages<br/>(tabela PostgreSQL)")]
    end

    subgraph "Poller (Scheduler)"
        C["OutboxPoller<br/>@Scheduled(1s)"] -->|"SELECT + claim"| B
        C --> D{"OutboxHandlerRegistry<br/>.findHandler(topic)"}
    end

    subgraph "Handlers"
        D -->|"JOB_SUBMITTED"| E["JobAdmissionWorker"]
        D -->|"JOB_QUEUED"| F["FifoJobDispatcherService"]
    end

    subgraph "Dedup Guard"
        G["OutboxConsumerDedupeService<br/>.runOnce(consumer, msgId)"]
        E --> G
        F --> G
    end

    style B fill:#3b82f6,color:#fff
    style C fill:#f59e0b,color:#000
```

### Tematy (Topics)

| Topic | Publisher | Handler |
|-------|----------|---------|
| `JobSubmitted` | [JobOutboxPublisher](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/JobOutboxPublisher.java) (po zapisie Job) | [JobAdmissionWorker](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/JobAdmissionWorker.java) |
| `JobQueued` | JobOutboxPublisher (po admisji) | [FifoJobDispatcherService](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/FifoJobDispatcherService.java) |

> [!IMPORTANT]
> Outbox Poller działa z claim-based locking — każda wiadomość jest „claimed" przez konkretnego workera na czas przetwarzania (`claim-timeout-ms: 60s`). W przypadku niepowodzenia, wiadomość jest oznaczana jako failed z `retry-delay-ms: 5s`.

---

## 10. Mechanizm leasingowy (Prepaid Billing)

System billing oparty jest na **prepaid lease** — minuty obliczeniowe są rezerwowane z góry i odnawialne.

```mermaid
sequenceDiagram
    participant AdmissionWorker
    participant QuotaAccounting
    participant LeaseWorker
    participant NomadJobControl
    participant StateMachine

    Note over AdmissionWorker: Admisja zadania
    AdmissionWorker->>QuotaAccounting: reserveInitialLease()
    QuotaAccounting->>QuotaAccounting: Zarezerwuj 15 min<br/>(QUOTA_LEASE_MINUTES)
    QuotaAccounting->>QuotaAccounting: Utwórz QuotaReservation (ACTIVE)
    QuotaAccounting->>QuotaAccounting: Zmniejsz available_minutes<br/>w UserQuotaBalanceCurrent

    Note over LeaseWorker: @Scheduled co 30s<br/>+ startup reconciliation
    LeaseWorker->>LeaseWorker: enforceDueLeases()<br/>Znajdź jobs z lease<br/>wygasającym w ciągu 120s

    alt Lease jeszcze aktywny
        LeaseWorker->>QuotaAccounting: reserveAdditionalLease()
        QuotaAccounting->>QuotaAccounting: Zarezerwuj kolejne 15 min
        LeaseWorker->>StateMachine: markLeaseRenewed()<br/>activeLeaseExpiresAt += 15min<br/>leaseSequence++
    else Lease wygasł
        LeaseWorker->>StateMachine: markLeaseStopRequested()
        LeaseWorker->>NomadJobControl: stopJob(nomadJobId)
        LeaseWorker->>LeaseWorker: settleCurrentLeaseIfNeeded()
        LeaseWorker->>QuotaAccounting: settleLeaseMinutes()<br/>(reserved → consumed + refunded)
        LeaseWorker->>StateMachine: markTimedOut(job, "LEASE_EXPIRED")
    else Brak kwoty na odnowienie
        LeaseWorker->>StateMachine: markLeaseStopRequested("LEASE_RENEWAL_FAILED")
        LeaseWorker->>NomadJobControl: stopJob(nomadJobId)
        LeaseWorker->>StateMachine: markTimedOut(job, "LEASE_RENEWAL_FAILED")
    end
```

### Kluczowe parametry lease

| Parametr | Wartość domyślna | Opis |
|----------|-----------------|------|
| `app.quota.lease-minutes` | 15 | Długość jednego lease'u (w minutach) |
| `app.scheduler.lease.interval-ms` | 30 000 | Interwał sprawdzania lease'ów |
| `app.scheduler.lease.safety-window-ms` | 120 000 | Okno wyprzedzające (2 min przed wygaśnięciem) |
| `app.scheduler.lease.batch-size` | 50 | Maks. jobs przetwarzanych w jednym cyklu |

---

## 11. System kwotowy

```mermaid
graph TD
    subgraph "Konfiguracja kwot"
        QP["QuotaPolicy<br/>(per rola, monthly_minutes,<br/>role_weight, unlimited)"]
        QO["UserQuotaOverride<br/>(per user, nadpisuje politykę roli)"]
    end

    subgraph "Rozwiązywanie polityki"
        QPR["QuotaPolicyResolverService"]
        QPR -->|"1. Sprawdź override"| QO
        QPR -->|"2. Fallback: polityka roli"| QP
        QPR -->|"wynik"| EP["EffectiveQuotaPolicy<br/>{role, monthlyMinutes,<br/>roleWeight, unlimited}"]
    end

    subgraph "Księgowość kwotowa"
        QAS["QuotaAccountingService"]
        QAS -->|"tworzy"| QG["QuotaGrant<br/>(ROLE_GRANT / ADMIN_BONUS)"]
        QAS -->|"tworzy/aktualizuje"| QR["QuotaReservation<br/>(ACTIVE → CONSUMED/RELEASED)"]
        QAS -->|"zarządza"| QB["UserQuotaBalanceCurrent<br/>{granted, reserved,<br/>consumed, available}"]
        QAS -->|"loguje"| QL["QuotaUsageLedger<br/>(USAGE_DEBITED / USAGE_RELEASED)"]
    end

    EP --> QAS

    style QAS fill:#059669,color:#fff
    style QB fill:#3b82f6,color:#fff
```

### Formuła salda

```
available_minutes = granted_minutes − reserved_minutes − consumed_minutes
```

### Typy grantów

| Typ | Źródło | Opis |
|-----|--------|------|
| `ROLE_GRANT` | Automatyczny | Tworzony przy pierwszym użyciu w danym miesiącu na podstawie polityki roli |
| `ADMIN_BONUS` | Ręczny (admin) | Dodatkowe minuty przyznane przez administratora |

---

## 12. Synchronizacja węzłów Nomad

```mermaid
sequenceDiagram
    participant Scheduler as NodeSyncService<br/>@Scheduled
    participant NomadClient as NomadNodeClient
    participant Nomad as Nomad API
    participant NodeRepo as NodeRepository
    participant DB as PostgreSQL

    Note over Scheduler: Uruchamiany przy starcie<br/>+ cyklicznie (interval-ms)

    Scheduler->>NomadClient: fetchClientNodes()
    NomadClient->>Nomad: GET /v1/nodes
    Nomad-->>NomadClient: Lista nodes + szczegóły

    loop Dla każdego NomadNodeSnapshot
        NomadClient-->>Scheduler: snapshot
        Scheduler->>NodeRepo: upsertFromNomad(snapshot)
        NodeRepo->>DB: INSERT ... ON CONFLICT DO UPDATE<br/>(hostname, ip, status, cpu, ram, gpu, ...)
    end

    Scheduler->>NodeRepo: markStaleAsOffline(threshold)
    NodeRepo->>DB: UPDATE nodes SET status = 'OFFLINE'<br/>WHERE last_heartbeat < threshold
```

> [!NOTE]
> Każdy node przechowuje informacje z Nomad: hostname, IP, status, scheduling eligibility, datacenter, node pool, CPU cores, RAM, wersje oprogramowania, oraz flagi (draining, GPU NVIDIA).

---

## 13. Dyspozycja zadań do Nomad

[FifoJobDispatcherService](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/FifoJobDispatcherService.java) odpowiada za wysyłanie zadań do Nomad:

```mermaid
flowchart TD
    A["Trigger: OutboxMessage JOB_QUEUED<br/>lub @Scheduled co 3s"] --> B{"Czy są stale<br/>DISPATCHING jobs?"}
    
    B -->|"Tak"| C["retryStaleDispatchingJob()<br/>(starsze niż 60s bez odpowiedzi)"]
    B -->|"Nie"| D["loadClusterCapacity()"]
    
    C --> D
    D --> E{"totalCpu > 0<br/>AND totalRamMb > 0?"}
    E -->|"Nie"| Z["Return (brak zasobów)"]
    E -->|"Tak"| F["claimNextDispatchableJob()<br/>SELECT ... FOR UPDATE<br/>(FIFO wg queued_at)"]
    
    F --> G{"Job znaleziony?"}
    G -->|"Nie"| Z
    G -->|"Tak"| H["Sprawdź:<br/>• status == QUEUED<br/>• lease aktywny<br/>• zasoby pasują do klastra"]
    
    H --> I["ensurePendingArtifact()"]
    I --> J["markDispatchRequested()"]
    J --> K["Zbuduj NomadDispatchRequest<br/>z job-template.hcl"]
    K --> L["nomadDispatchClient.dispatchJob()"]
    
    L -->|"Sukces"| M["markDispatchAccepted()<br/>status → SCHEDULING"]
    L -->|"Błąd"| N["compensateDispatchFailure()"]
    N --> O["quotaAccountingService<br/>.refundLeaseReservation()"]
    O --> P["markCurrentLeaseSettled(consumed=0)"]
    P --> Q["markDispatchFailed()<br/>status → INFRA_FAILED"]

    style A fill:#f59e0b,color:#000
    style M fill:#059669,color:#fff
    style Q fill:#dc2626,color:#fff
```

### Nomad Job Template (HCL)

Szablon [job-template.hcl](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/nomad/job-template.hcl) definiuje zadanie Nomad z **dwoma taskami**:

| Task | Typ | Opis |
|------|-----|------|
| `user-workload` | główny | Uruchamia kontener Docker użytkownika z zadanym `DOCKER_IMAGE` i `EXECUTION_COMMAND` |
| `artifact-uploader` | `poststop` | Po zakończeniu user-workload: pakuje `/alloc/data` do ZIP i uploaduje na MinIO via presigned URL |

---

## 14. Przechowywanie artefaktów (S3/MinIO)

```mermaid
sequenceDiagram
    participant NomadTask as artifact-uploader<br/>(Nomad poststop)
    participant MinIO as MinIO S3
    participant ArtifactSvc as JobArtifactService<br/>@Scheduled(10s)
    participant StorageSvc as StorageService
    participant StateMachine
    participant User
    participant StorageRes as StorageResource

    Note over NomadTask: Po zakończeniu user-workload
    NomadTask->>NomadTask: zip -r /tmp/output.zip<br/>/alloc/data/
    NomadTask->>MinIO: PUT (presigned URL)<br/>artifacts/{userId}/{jobId}/output.zip

    Note over ArtifactSvc: Finalizer scan (co 10s)
    ArtifactSvc->>ArtifactSvc: findTop100 by FINALIZING<br/>ordered by processFinishedAt
    ArtifactSvc->>StorageSvc: getObjectMetadata(objectKey)
    StorageSvc->>MinIO: HEAD object

    alt Obiekt istnieje
        ArtifactSvc->>ArtifactSvc: artifact.status = AVAILABLE<br/>Zapisz sizeBytes, checksum
        ArtifactSvc->>StateMachine: markArtifactAvailable()
        Note over StateMachine: status → SUCCEEDED
    else Obiekt nie istnieje
        ArtifactSvc->>ArtifactSvc: artifact.status = MISSING
        ArtifactSvc->>StateMachine: markFinalizedWithoutArtifact()
        Note over StateMachine: status → SUCCEEDED (bez artefaktu)
    end

    User->>StorageRes: GET /api/jobs/{id}/artifact-download-url
    StorageRes->>ArtifactSvc: getVerifiedDownloadableArtifact()
    StorageRes->>StorageSvc: generatePresignedDownloadUrl()
    StorageSvc->>MinIO: Presign GET
    StorageSvc-->>StorageRes: presigned URL (TTL: 900s)
    StorageRes-->>User: {url, objectKey, expiresInSec}
```

### Struktura kluczy S3

| Typ | Wzorzec klucza |
|-----|----------------|
| Artefakty | `artifacts/{userId}/{jobId}/output.zip` |
| Logi | `logs/{userId}/{jobId}/{stdout\|stderr}/{sequence}.log` |

---

## 15. Przechwytywanie logów

[JobLogCaptureService](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/JobLogCaptureService.java) cyklicznie pobiera logi z Nomad:

```mermaid
flowchart TD
    A["@Scheduled co 5s<br/>captureJobLogs()"] --> B["Znajdź do 50 jobs<br/>w statusach: DISPATCHING →<br/>INFRA_FAILED"]
    B --> C["Dla każdego job:"]
    
    C --> D["captureStream(jobId, STDOUT)"]
    C --> E["captureStream(jobId, STDERR)"]
    
    D --> F["ensureStream()<br/>(utwórz/pobierz JobLogStream)"]
    F --> G{"captureComplete?"}
    G -->|"Tak"| H["Pomiń"]
    G -->|"Nie"| I["nomadLogsClient<br/>.listJobAllocations()"]
    
    I --> J["selectRelevantAllocation()"]
    J --> K["nomadLogsClient<br/>.streamAllocationLogs()"]
    
    K --> L["Dla każdego NomadLogFrame:"]
    L --> M["persistChunk()<br/>1. Zapisz treść w S3<br/>   (logObjectStorage)<br/>2. Utwórz JobLogChunk<br/>3. Aktualizuj lastOffset"]
    
    L --> N{"Terminal & brak<br/>nowych chunków?"}
    N -->|"Tak"| O["markComplete()<br/>captureComplete = true"]

    style A fill:#f59e0b,color:#000
    style M fill:#4f46e5,color:#fff
```

> [!TIP]
> Logi mogą być przechowywane w S3 (`S3JobLogObjectStorage`) lub w pamięci (`InMemoryJobLogObjectStorage`), zależnie od konfiguracji profilu Spring.

---

## 16. API REST — mapa endpointów

Wszystkie endpointy mają prefix `/api` (via `server.servlet.context-path`).

### Jobs

| Metoda | Ścieżka | Opis | Autoryzacja |
|--------|---------|------|-------------|
| `GET` | `/jobs` | Lista zadań użytkownika (paginacja, filtr statusu) | Authenticated |
| `GET` | `/jobs/{id}` | Szczegóły zadania | Authenticated (owner) |
| `GET` | `/jobs/{id}/events` | SSE stream szczegółów zadania | Authenticated (owner) |
| `GET` | `/jobs/{id}/logs` | Logi zadania (stdout/stderr) | Authenticated (owner) |
| `POST` | `/jobs` | Przesłanie nowego zadania | Authenticated |
| `POST` | `/jobs/{id}/cancel` | Anulowanie zadania | Authenticated (owner) |
| `GET` | `/jobs/{id}/artifact-download-url` | Presigned download URL artefaktu | Authenticated (owner) |

### Quota

| Metoda | Ścieżka | Opis | Autoryzacja |
|--------|---------|------|-------------|
| `GET` | `/quota/me` | Podsumowanie kwoty użytkownika | Authenticated |
| `GET` | `/quota/usage-ledger` | Historia użycia kwoty (per miesiąc) | Authenticated |

### Admin Quota

| Metoda | Ścieżka | Opis | Autoryzacja |
|--------|---------|------|-------------|
| `GET` | `/admin/quota/users` | Lista użytkowników (do grantów) | ADMIN |
| `PUT` | `/admin/quota/policies/{role}` | Upsert polityki kwotowej dla roli | ADMIN |
| `PUT` | `/admin/quota/overrides/{userId}` | Upsert override kwotowego dla użytkownika | ADMIN |
| `POST` | `/admin/quota/grants` | Przyznanie dodatkowych minut | ADMIN |

### Nodes

| Metoda | Ścieżka | Opis | Autoryzacja |
|--------|---------|------|-------------|
| `GET` | `/nodes` | Lista węzłów klastra | ADMIN |
| `GET` | `/nodes/{id}` | Szczegóły węzła | ADMIN |
| `GET` | `/nodes/gpu-available` | Czy GPU jest dostępne | Authenticated |

### Inne

| Metoda | Ścieżka | Opis | Autoryzacja |
|--------|---------|------|-------------|
| `GET` | `/actuator/health` | Health check | Public |
| `GET` | `/v3/api-docs/**` | OpenAPI spec (domyślnie wyłączone) | Public |

---

## 17. Frontend — architektura i routing

Frontend oparty na **Next.js 16** (App Router) z TypeScript i Tailwind CSS 4.

```mermaid
graph TD
    subgraph "Next.js App Router"
        Layout["layout.tsx<br/>(root layout)"]
        
        subgraph "Publiczne"
            Login["app/login/page.tsx"]
            Signup["app/signup/page.tsx"]
        end
        
        subgraph "Dashboard (chronione)"
            Home["app/(dashboard)/page.tsx"]
        end
        
        subgraph "Jobs (chronione)"
            JobsList["app/jobs/page.tsx<br/>Lista zadań"]
            JobNew["app/jobs/new/page.tsx<br/>Nowe zadanie"]
            JobDetail["app/jobs/[id]/page.tsx<br/>Szczegóły zadania"]
        end
        
        subgraph "Nodes (chronione, ADMIN)"
            NodesList["app/nodes/page.tsx<br/>Lista węzłów"]
            NodeDetail["app/nodes/[id]/page.tsx<br/>Szczegóły węzła"]
        end
        
        subgraph "Admin (chronione, ADMIN)"
            AdminPage["app/admin/page.tsx<br/>Panel administratora"]
            AdminQuotaGrant["admin-quota-grant-form.tsx"]
        end
        
        subgraph "API Routes"
            AuthAPI["app/api/auth/<br/>refresh, callback"]
        end
    end
    
    subgraph "Middleware"
        Proxy["proxy.ts<br/>(auth guard,<br/>token refresh)"]
    end
    
    subgraph "Biblioteki"
        Auth["lib/auth.ts"]
        BackendUrl["lib/backend-url.ts"]
        BrokerProxy["lib/broker-proxy.ts"]
        ClientAuth["lib/client-auth.ts"]
        ServerAuth["lib/server-auth.ts"]
        DateTime["lib/date-time.ts"]
        Duration["lib/duration.ts"]
        UserRoleLib["lib/user-role.ts"]
    end
    
    subgraph "Komponenty"
        Header["components/header.tsx"]
        LogoutBtn["components/logout-button.tsx"]
        UILib["components/ui/*<br/>(shadcn/ui)"]
    end

    Proxy --> Layout
    Layout --> Home
    Layout --> JobsList
    Layout --> NodesList
    Layout --> AdminPage
    
    style Proxy fill:#f59e0b,color:#000
    style Layout fill:#0f172a,color:#fff
```

### Middleware (proxy.ts)

[proxy.ts](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/frontend/src/proxy.ts) pełni rolę auth guard:

| Warunek | Akcja |
|---------|-------|
| Strona auth + `access_token` | Redirect → `/` (lub `?next=...`) |
| `refresh_token` bez `access_token` | Redirect → `/api/auth/refresh` |
| Strona chroniona bez tokena | Redirect → `/login?next=...` |

---

## 18. Background Workers i Schedulery

System wykorzystuje `@Scheduled` annotacje Spring do cyklicznych zadań:

```mermaid
gantt
    title Background Workers — interwały
    dateFormat X
    axisFormat %s

    section Outbox
    OutboxPoller (1s)               :active, 0, 1
    OutboxPoller (1s)               :active, 1, 2
    OutboxPoller (1s)               :active, 2, 3

    section Dispatcher
    FifoJobDispatcher (3s)          :active, 0, 3

    section Logs
    JobLogCaptureService (5s)       :active, 0, 5

    section Artifacts
    JobArtifactFinalizer (10s)      :active, 0, 10

    section Nodes
    NodeSyncService (configurable)  :active, 0, 10

    section Lease
    LeaseWorker (30s)               :active, 0, 30
```

| Worker | Klasa | Interwał | Cel |
|--------|-------|----------|-----|
| **OutboxPoller** | [OutboxPoller](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/events/service/OutboxPoller.java) | 1s | Polling outbox messages |
| **FifoJobDispatcher** | [FifoJobDispatcherService](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/FifoJobDispatcherService.java) | 3s | Dispatch queued jobs do Nomad |
| **LeaseWorker** | [LeaseWorker](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/LeaseWorker.java) | 30s | Enforcement/renewal lease'ów |
| **JobLogCapture** | [JobLogCaptureService](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/jobs/service/JobLogCaptureService.java) | 5s | Capture stdout/stderr z Nomad |
| **ArtifactFinalizer** | [JobArtifactService](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/storage/service/JobArtifactService.java) | 10s | Sprawdzanie artefaktów na S3 |
| **NodeSync** | [NodeSyncService](file:///c:/Users/bielo/IdeaProjects/private-cloud-resource-manager/backend/src/main/java/com/pcrm/backend/nodes/service/NodeSyncService.java) | konfigurowalny | Synchronizacja stanu węzłów z Nomad |
| **IdempotencyCleanup** | — | cron (co godzinę) | Czyszczenie starych rekordów idempotencji |

---

## 19. Idempotencja

System zapewnia idempotencję operacji krytycznych (np. submission zadania, admin grant kwotowy) poprzez wzorzec **Idempotency Key**:

```mermaid
flowchart TD
    A["Klient wysyła request<br/>z nagłówkiem Idempotency-Key"] --> B{"IdempotencyRecord<br/>istnieje?"}
    
    B -->|"Nie"| C["Utwórz IdempotencyRecord<br/>status = STARTED"]
    C --> D["Wykonaj workflow"]
    D --> E["Zapisz wynik<br/>w response_body (JSON)"]
    E --> F["status = COMPLETED"]
    F --> G["Zwróć wynik<br/>(HTTP 202 Accepted)"]
    
    B -->|"Tak, COMPLETED"| H["Odtwórz wynik<br/>z response_body"]
    H --> I["Zwróć replayed wynik<br/>(HTTP 200 OK)"]
    
    B -->|"Tak, STARTED"| J["Konflikt —<br/>operacja w toku"]

    style G fill:#059669,color:#fff
    style I fill:#3b82f6,color:#fff
```

---

## 20. Podsumowanie

```mermaid
mindmap
    root((PCRM))
        Control Plane
            Spring Boot 4
            REST API
            JWT Auth (Supabase)
            Flyway Migrations
        Execution Plane
            Nomad 1.7
            Docker Containers
            HCL Job Templates
        Data Layer
            PostgreSQL 15
            14 migration files
            Transactional Outbox
        Storage
            MinIO S3
            Presigned URLs
            Artifacts & Logs
        Quota System
            Prepaid Lease Billing
            Role-based Policies
            User Overrides
            Admin Grants
        Frontend
            Next.js 16
            React 19
            Tailwind CSS 4
            shadcn/ui
```

### Kluczowe wzorce architektoniczne

| Wzorzec | Implementacja |
|---------|--------------|
| **Transactional Outbox** | `OutboxMessage` → `OutboxPoller` → handlery |
| **State Machine** | `JobStateMachine` — 11 stanów, przejścia z walidacją |
| **FIFO Queue** | `SELECT ... ORDER BY queued_at FOR UPDATE` |
| **Lease Renewal** | `LeaseWorker` — cykliczne odnawianie z rezerwacją kwoty |
| **Idempotency Key** | `IdempotencyRecord` — at-most-once semantics |
| **Consumer Dedup** | `OutboxConsumerDedupeService` — exactly-once processing |
| **Presigned URL** | Upload/download artefaktów bez proxy-owania przez backend |
| **RBAC** | `@PreAuthorize("hasRole('ADMIN')")` + role z JWT |
| **Event-Driven** | Outbox → handler (in-process, polling-based) |
| **Pessimistic Locking** | `FOR UPDATE` na jobs i quota balance |
