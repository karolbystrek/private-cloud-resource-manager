# Graph Report - .  (2026-05-22)

## Corpus Check
- Corpus is ~47,308 words - fits in a single context window. You may not need a graph.

## Summary
- 1138 nodes · 1920 edges · 117 communities (83 shown, 34 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 283 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Job Orchestration Core|Job Orchestration Core]]
- [[_COMMUNITY_Quota Reservations|Quota Reservations]]
- [[_COMMUNITY_UI Header Theme|UI Header Theme]]
- [[_COMMUNITY_Security Config|Security Config]]
- [[_COMMUNITY_Spring Profiles|Spring Profiles]]
- [[_COMMUNITY_Nomad HTTP Clients|Nomad HTTP Clients]]
- [[_COMMUNITY_Event Outbox|Event Outbox]]
- [[_COMMUNITY_Job Env Editor|Job Env Editor]]
- [[_COMMUNITY_Job Submission API|Job Submission API]]
- [[_COMMUNITY_Storage Artifacts|Storage Artifacts]]
- [[_COMMUNITY_Frontend Dependencies|Frontend Dependencies]]
- [[_COMMUNITY_Auth Idempotency|Auth Idempotency]]
- [[_COMMUNITY_Admin Dashboard|Admin Dashboard]]
- [[_COMMUNITY_Exception Types|Exception Types]]
- [[_COMMUNITY_Event Dispatch Workers|Event Dispatch Workers]]
- [[_COMMUNITY_Path Aliases|Path Aliases]]
- [[_COMMUNITY_Jobs History UI|Jobs History UI]]
- [[_COMMUNITY_Job Types Frontend|Job Types Frontend]]
- [[_COMMUNITY_TypeScript Config|TypeScript Config]]
- [[_COMMUNITY_App Layout|App Layout]]
- [[_COMMUNITY_Auth API Routes|Auth API Routes]]
- [[_COMMUNITY_Broker Proxy|Broker Proxy]]
- [[_COMMUNITY_Node Query|Node Query]]
- [[_COMMUNITY_Prettier Config|Prettier Config]]
- [[_COMMUNITY_Date Format Utils|Date Format Utils]]
- [[_COMMUNITY_Artifact Download|Artifact Download]]
- [[_COMMUNITY_Graphify Detect|Graphify Detect]]
- [[_COMMUNITY_Global Exception Handler|Global Exception Handler]]
- [[_COMMUNITY_Job Form Fields|Job Form Fields]]
- [[_COMMUNITY_Artifact API Types|Artifact API Types]]
- [[_COMMUNITY_Log Stream Panel|Log Stream Panel]]
- [[_COMMUNITY_Node Details UI|Node Details UI]]
- [[_COMMUNITY_Home Dashboard|Home Dashboard]]
- [[_COMMUNITY_Root Package|Root Package]]
- [[_COMMUNITY_Event Dedupe IDs|Event Dedupe IDs]]
- [[_COMMUNITY_Supabase Key Scripts|Supabase Key Scripts]]
- [[_COMMUNITY_Idempotency Domain|Idempotency Domain]]
- [[_COMMUNITY_Job Domain Entity|Job Domain Entity]]
- [[_COMMUNITY_Quota Grant Entity|Quota Grant Entity]]
- [[_COMMUNITY_Nodes REST API|Nodes REST API]]
- [[_COMMUNITY_Outbox Handlers|Outbox Handlers]]
- [[_COMMUNITY_Env Var Validation|Env Var Validation]]
- [[_COMMUNITY_Backend Application|Backend Application]]
- [[_COMMUNITY_Backend Tests|Backend Tests]]
- [[_COMMUNITY_Nomad Dispatch|Nomad Dispatch]]
- [[_COMMUNITY_Nomad Node Client|Nomad Node Client]]
- [[_COMMUNITY_Job Artifact Entity|Job Artifact Entity]]
- [[_COMMUNITY_Transaction Template|Transaction Template]]
- [[_COMMUNITY_Domain Event Entity|Domain Event Entity]]
- [[_COMMUNITY_Consumer Dedupe Entity|Consumer Dedupe Entity]]
- [[_COMMUNITY_Outbox Message Entity|Outbox Message Entity]]
- [[_COMMUNITY_Nomad Job Control|Nomad Job Control]]
- [[_COMMUNITY_Outbox Repository|Outbox Repository]]
- [[_COMMUNITY_Aggregate Sequence|Aggregate Sequence]]
- [[_COMMUNITY_Domain Event Repo|Domain Event Repo]]
- [[_COMMUNITY_Dedupe Repository|Dedupe Repository]]
- [[_COMMUNITY_Nomad Stream Cursor|Nomad Stream Cursor]]
- [[_COMMUNITY_Quota Policy Entity|Quota Policy Entity]]
- [[_COMMUNITY_Scheduling Config|Scheduling Config]]
- [[_COMMUNITY_Usage Ledger Entity|Usage Ledger Entity]]
- [[_COMMUNITY_Quota Balance Entity|Quota Balance Entity]]
- [[_COMMUNITY_Quota Override Entity|Quota Override Entity]]
- [[_COMMUNITY_ESLint Legacy|ESLint Legacy]]
- [[_COMMUNITY_Next.js Config|Next.js Config]]
- [[_COMMUNITY_PostCSS Config|PostCSS Config]]
- [[_COMMUNITY_Sequence Repository|Sequence Repository]]
- [[_COMMUNITY_Grant Repository|Grant Repository]]
- [[_COMMUNITY_Stream Cursor Repo|Stream Cursor Repo]]

## God Nodes (most connected - your core abstractions)
1. `cn()` - 66 edges
2. `build` - 29 edges
3. `QuotaAccountingService` - 27 edges
4. `FairQueueDispatcherService` - 24 edges
5. `NomadEventStreamListener` - 23 edges
6. `brokerFetch()` - 18 edges
7. `JobStateMachine` - 18 edges
8. `Button()` - 17 edges
9. `compilerOptions` - 16 edges
10. `getBackendUrlForServer()` - 15 edges

## Surprising Connections (you probably didn't know these)
- `Prepaid Quota Lease Minutes` --semantically_similar_to--> `On-Prem Batch Cloud`  [INFERRED] [semantically similar]
  backend/src/main/resources/application.yaml → README.md
- `Kong Key-Auth and ACL Pattern` --semantically_similar_to--> `Supabase JWT Validation`  [INFERRED] [semantically similar]
  volumes/api/kong.yml → backend/src/main/resources/application.yaml
- `Self-Hosted Supabase` --references--> `Kong API Gateway Service`  [INFERRED]
  README.md → docker-compose.yml
- `S3-Compatible Storage Config` --shares_data_with--> `Supabase Storage API Service`  [INFERRED]
  backend/src/main/resources/application.yaml → docker-compose.yml
- `cn()` --calls--> `clsx`  [INFERRED]
  frontend/src/lib/utils.ts → frontend/package.json

## Hyperedges (group relationships)
- **PCRM Control Plane, Nomad Execution, Supabase Identity/Storage** — readme_spring_boot_control_plane, readme_nomad_execution, readme_self_hosted_supabase [EXTRACTED 1.00]
- **Kong Gateway Routing to Supabase Microservices** — docker_compose_kong, kong_declarative_config, docker_compose_auth, docker_compose_rest, docker_compose_storage, docker_compose_studio [INFERRED 0.88]
- **Docker Log Collection to Logflare Analytics** — docker_compose_vector, vector_log_pipeline, vector_logflare_sinks, docker_compose_analytics [EXTRACTED 0.95]

## Communities (117 total, 34 thin omitted)

### Community 0 - "Job Orchestration Core"
Cohesion: 0.05
Nodes (8): Node, FairQueueDispatcherService, JobStateMachine, LeaseWorker, none(), stop(), usageRatio(), NomadEventStreamListener

### Community 1 - "Quota Reservations"
Cohesion: 0.06
Nodes (12): QuotaReservation, start, status(), QuotaReservationRepository, QuotaUsageLedgerRepository, UserQuotaBalanceCurrentRepository, UserQuotaOverrideRepository, QuotaResource (+4 more)

### Community 2 - "UI Header Theme"
Cohesion: 0.06
Nodes (50): HeaderProps, LogoutButton(), ThemeOption, cn(), AlertDialog(), AlertDialogAction(), AlertDialogCancel(), AlertDialogContent() (+42 more)

### Community 3 - "Security Config"
Cohesion: 0.05
Nodes (14): JacksonConfig, JwtDecoderConfig, SecurityConfig, StorageConfig, build, NomadHttpLogsClient, chunk(), heartbeat() (+6 more)

### Community 4 - "Spring Profiles"
Cohesion: 0.06
Nodes (52): Development Spring Profile, Production Spring Profile, H2 In-Memory Test Database, Test Spring Profile, Backend Base Configuration, Transactional Outbox Pattern, Flyway Database Migrations, Idempotency Key Cleanup (+44 more)

### Community 5 - "Nomad HTTP Clients"
Cohesion: 0.05
Nodes (18): TestNomadConfig, NomadIntegrationConfig, NomadHttpDispatchClient, NomadHttpJobControlClient, NomadHttpNodeClient, envVars, envVarsObj, error (+10 more)

### Community 6 - "Event Outbox"
Cohesion: 0.07
Nodes (8): OutboxClaimRepository, AggregateSequenceService, DomainEventAppender, EventTopics, JobEventPublisher, OutboxHandlerRegistry, OutboxPoller, OutboxWriter

### Community 7 - "Job Env Editor"
Cohesion: 0.09
Nodes (24): collectEnvVarErrorMessages(), createEnvVarRow(), EnvVarsEditor(), EnvVarsEditorProps, envVarFieldErrorMessages, EnvVarRow, toEnvVarFieldErrors(), toEnvVarsMap() (+16 more)

### Community 8 - "Job Submission API"
Cohesion: 0.06
Nodes (9): JobRepository, ProfileRepository, AdminQuotaResource, JobsResource, JobQueryService, JobSubmissionPersistenceService, JobSubmissionService, created() (+1 more)

### Community 9 - "Storage Artifacts"
Cohesion: 0.09
Nodes (7): url, JobArtifactRepository, InternalStorageResource, StorageResource, JobArtifactService, missing(), StorageService

### Community 10 - "Frontend Dependencies"
Cohesion: 0.05
Nodes (37): dependencies, class-variance-authority, clsx, next, next-themes, postcss, radix-ui, react (+29 more)

### Community 11 - "Auth Idempotency"
Cohesion: 0.09
Nodes (6): SupabaseJwtAuthenticationConverter, getAuthorities(), IdempotencyRecordRepository, IdempotencyCleanupService, IdempotencyService, ProfileProvisioningService

### Community 12 - "Admin Dashboard"
Cohesion: 0.14
Nodes (18): AdminDashboardPage(), fetchNodes(), getCountByStatus(), metadata, NodesResult, HomeDashboardSkeleton(), FieldRowProps, formatRamGb() (+10 more)

### Community 13 - "Exception Types"
Cohesion: 0.08
Nodes (9): IdempotencyConflictException, IdempotencyInProgressException, InsufficientQuotaException, InvalidIdempotencyKeyException, NomadDispatchException, NomadJobControlException, ResourceNotFoundException, NomadLogsUnavailableException (+1 more)

### Community 14 - "Event Dispatch Workers"
Cohesion: 0.13
Nodes (4): OutboxMessageHandler, AggregateIds, EventConsumerDedupeService, JobAdmissionWorker

### Community 15 - "Path Aliases"
Cohesion: 0.09
Nodes (21): aliases, components, hooks, lib, ui, utils, iconLibrary, menuAccent (+13 more)

### Community 16 - "Jobs History UI"
Cohesion: 0.15
Nodes (16): buildJobsHref(), JobsHistoryView(), JobsHistoryViewProps, STATUS_OPTIONS, JobHistorySortDirection, JobStatus, buildJobsPath(), JobsPage() (+8 more)

### Community 17 - "Job Types Frontend"
Cohesion: 0.16
Nodes (17): JobDetails, JobHistoryItem, JobsPageResponse, FAILED_STATUSES, fetchQuotaSummary(), fetchRecentJobs(), fetchStatusCount(), fetchStatusCounts() (+9 more)

### Community 18 - "TypeScript Config"
Cohesion: 0.10
Nodes (19): compilerOptions, allowJs, esModuleInterop, incremental, isolatedModules, jsx, lib, module (+11 more)

### Community 19 - "App Layout"
Cohesion: 0.17
Nodes (15): ibmPlexMono, ibmPlexSans, metadata, RootLayout(), Header(), ThemeProvider(), setAuthCookies(), isUserRole() (+7 more)

### Community 20 - "Auth API Routes"
Cohesion: 0.23
Nodes (12): GET(), baseCookieOptions, buildClearSessionPath(), buildLoginPath(), clearAuthCookies(), isSafeRedirectTarget(), POST(), AUTH_PATHS (+4 more)

### Community 21 - "Broker Proxy"
Cohesion: 0.17
Nodes (15): ApiErrorResponse, BrokerFieldError, BrokerProblemDetail, buildFieldErrors(), envVarErrorMessages, isRecord(), JobSubmissionBody, JobSubmissionFieldErrors (+7 more)

### Community 22 - "Node Query"
Cohesion: 0.16
Nodes (3): NodeRepository, NodeQueryService, NodeSyncService

### Community 23 - "Prettier Config"
Cohesion: 0.13
Nodes (14): arrowParens, bracketSameLine, bracketSpacing, endOfLine, importOrder, jsxSingleQuote, plugins, printWidth (+6 more)

### Community 24 - "Date Format Utils"
Cohesion: 0.27
Nodes (11): formatDateForUser(), formatCountdown(), formatDateForUser(), formatHeartbeat(), NodesList(), NodesListProps, formatLocalDateTime(), formatLocalMonthDay() (+3 more)

### Community 25 - "Artifact Download"
Cohesion: 0.24
Nodes (9): ArtifactDownloadResponse, GET(), Params, GET(), Params, BackendProblem, BrokerJsonProxyOptions, proxyBrokerJsonGet() (+1 more)

### Community 26 - "Graphify Detect"
Cohesion: 0.14
Nodes (13): files, code, document, image, paper, video, graphifyignore_patterns, needs_graph (+5 more)

### Community 28 - "Job Form Fields"
Cohesion: 0.22
Nodes (10): ACTIVE_STATUSES, ArtifactDownloadPayload, FieldRowProps, formatDateForUser(), JobDetailsPanel(), JobDetailsPanelProps, Tooltip(), TooltipContent() (+2 more)

### Community 29 - "Artifact API Types"
Cohesion: 0.24
Nodes (10): ArtifactDownloadResponse, BackendProblem, buildFileName(), GET(), Params, getBackendUrlForServer(), GET(), normalizeOffset() (+2 more)

### Community 30 - "Log Stream Panel"
Cohesion: 0.20
Nodes (9): ChunkEventPayload, ConnectionState, getConnectionBadgeClassName(), getConnectionBadgeLabel(), JobLogsPanel(), JobLogsPanelProps, LogStream, StatusEventPayload (+1 more)

### Community 31 - "Node Details UI"
Cohesion: 0.23
Nodes (9): NodeDetails, NodeSummary, metadata, NodeDetailPage(), NodeDetailPageProps, resolvePollIntervalMs(), metadata, NodesPage() (+1 more)

### Community 32 - "Home Dashboard"
Cohesion: 0.36
Nodes (7): buildJobsHistoryHref(), clampPercentage(), getQuotaBarSegments(), HomeDashboard(), HomeDashboardProps, StatusCounts, formatMinutesAsHoursAndMinutes()

### Community 33 - "Root Package"
Cohesion: 0.25
Nodes (7): devDependencies, husky, name, private, scripts, prepare, workspaces

### Community 34 - "Event Dedupe IDs"
Cohesion: 0.60
Nodes (3): AggregateSequenceId, EventConsumerDedupeId, Serializable

## Knowledge Gaps
- **191 isolated node(s):** `code`, `document`, `paper`, `image`, `video` (+186 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **34 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `cn()` connect `UI Header Theme` to `Job Env Editor`, `Frontend Dependencies`, `Admin Dashboard`, `App Layout`, `Job Form Fields`?**
  _High betweenness centrality (0.293) - this node is a cross-community bridge._
- **Why does `scripts` connect `Frontend Dependencies` to `Quota Reservations`, `Security Config`?**
  _High betweenness centrality (0.286) - this node is a cross-community bridge._
- **Are the 28 inferred relationships involving `build` (e.g. with `.job()` and `.jsonMapper()`) actually correct?**
  _`build` has 28 INFERRED edges - model-reasoned connections that need verification._
- **What connects `code`, `document`, `paper` to the rest of the system?**
  _196 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Job Orchestration Core` be split into smaller, more focused modules?**
  _Cohesion score 0.052659716653301256 - nodes in this community are weakly interconnected._
- **Should `Quota Reservations` be split into smaller, more focused modules?**
  _Cohesion score 0.059076682316118935 - nodes in this community are weakly interconnected._
- **Should `UI Header Theme` be split into smaller, more focused modules?**
  _Cohesion score 0.061815336463223784 - nodes in this community are weakly interconnected._