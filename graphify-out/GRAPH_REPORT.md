# Graph Report - private-cloud-resource-manager  (2026-05-26)

## Corpus Check
- 263 files · ~44,332 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1337 nodes · 2307 edges · 148 communities (99 shown, 49 thin omitted)
- Extraction: 84% EXTRACTED · 16% INFERRED · 0% AMBIGUOUS · INFERRED: 379 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `787b2870`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 63|Community 63]]
- [[_COMMUNITY_Community 64|Community 64]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 69|Community 69]]
- [[_COMMUNITY_Community 70|Community 70]]
- [[_COMMUNITY_Community 71|Community 71]]
- [[_COMMUNITY_Community 73|Community 73]]
- [[_COMMUNITY_Community 74|Community 74]]
- [[_COMMUNITY_Community 76|Community 76]]
- [[_COMMUNITY_Community 117|Community 117]]
- [[_COMMUNITY_Community 119|Community 119]]
- [[_COMMUNITY_Community 120|Community 120]]
- [[_COMMUNITY_Community 122|Community 122]]
- [[_COMMUNITY_Community 123|Community 123]]
- [[_COMMUNITY_Community 124|Community 124]]
- [[_COMMUNITY_Community 125|Community 125]]
- [[_COMMUNITY_Community 126|Community 126]]
- [[_COMMUNITY_Community 127|Community 127]]
- [[_COMMUNITY_Community 128|Community 128]]
- [[_COMMUNITY_Community 129|Community 129]]
- [[_COMMUNITY_Community 130|Community 130]]
- [[_COMMUNITY_Community 131|Community 131]]
- [[_COMMUNITY_Community 132|Community 132]]
- [[_COMMUNITY_Community 133|Community 133]]
- [[_COMMUNITY_Community 134|Community 134]]
- [[_COMMUNITY_Community 135|Community 135]]
- [[_COMMUNITY_Community 136|Community 136]]
- [[_COMMUNITY_Community 137|Community 137]]
- [[_COMMUNITY_Community 138|Community 138]]
- [[_COMMUNITY_Community 139|Community 139]]
- [[_COMMUNITY_Community 140|Community 140]]
- [[_COMMUNITY_Community 142|Community 142]]
- [[_COMMUNITY_Community 144|Community 144]]
- [[_COMMUNITY_Community 146|Community 146]]
- [[_COMMUNITY_Community 147|Community 147]]
- [[_COMMUNITY_Community 148|Community 148]]
- [[_COMMUNITY_Community 149|Community 149]]
- [[_COMMUNITY_Community 151|Community 151]]
- [[_COMMUNITY_Community 153|Community 153]]
- [[_COMMUNITY_Community 154|Community 154]]
- [[_COMMUNITY_Community 155|Community 155]]
- [[_COMMUNITY_Community 156|Community 156]]
- [[_COMMUNITY_Community 158|Community 158]]

## God Nodes (most connected - your core abstractions)
1. `cn()` - 66 edges
2. `build` - 38 edges
3. `QuotaAccountingService` - 25 edges
4. `FairQueueDispatcherService` - 24 edges
5. `NomadEventStreamListener` - 23 edges
6. `FifoJobDispatcherService` - 21 edges
7. `brokerFetch()` - 20 edges
8. `getBackendUrlForServer()` - 20 edges
9. `status()` - 20 edges
10. `JobStateMachine` - 18 edges

## Surprising Connections (you probably didn't know these)
- `Spring Boot Control Plane` --references--> `Spring Boot Backend Service`  [INFERRED]
  README.md → docker-compose.yml
- `Self-Hosted Supabase` --references--> `Kong API Gateway Service`  [INFERRED]
  README.md → docker-compose.yml
- `cn()` --calls--> `clsx`  [INFERRED]
  frontend/src/lib/utils.ts → frontend/package.json
- `Nomad Workload Execution` --references--> `HashiCorp Nomad Agent`  [INFERRED]
  README.md → docker-compose.yml
- `reset.sh Stack Reset` --references--> `private-cloud-resource-manager Compose Stack`  [EXTRACTED]
  README.md → docker-compose.yml

## Hyperedges (group relationships)
- **PCRM Control Plane, Nomad Execution, Supabase Identity/Storage** — readme_spring_boot_control_plane, readme_nomad_execution, readme_self_hosted_supabase [EXTRACTED 1.00]
- **Kong Gateway Routing to Supabase Microservices** — docker_compose_kong, kong_declarative_config, docker_compose_auth, docker_compose_rest, docker_compose_storage, docker_compose_studio [INFERRED 0.88]
- **Docker Log Collection to Logflare Analytics** — docker_compose_vector, vector_log_pipeline, vector_logflare_sinks, docker_compose_analytics [EXTRACTED 0.95]

## Communities (148 total, 49 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.05
Nodes (10): Node, id, JobArtifactRepository, JobArtifactService, JobStateMachine, LeaseWorker, none(), stop() (+2 more)

### Community 1 - "Community 1"
Cohesion: 0.06
Nodes (12): QuotaReservation, start, status(), QuotaReservationRepository, QuotaUsageLedgerRepository, UserQuotaBalanceCurrentRepository, UserQuotaOverrideRepository, QuotaResource (+4 more)

### Community 2 - "Community 2"
Cohesion: 0.15
Nodes (15): cn(), Avatar(), AvatarBadge(), AvatarFallback(), AvatarGroup(), AvatarGroupCount(), AvatarImage(), Separator() (+7 more)

### Community 3 - "Community 3"
Cohesion: 0.05
Nodes (9): JobRepository, ProfileRepository, AdminQuotaResource, JobsResource, JobQueryService, JobSubmissionPersistenceService, JobSubmissionService, created() (+1 more)

### Community 4 - "Community 4"
Cohesion: 0.09
Nodes (38): H2 In-Memory Test Database, Test Spring Profile, Backend Build Job, Backend Test Job, Frontend Build Job, Frontend Test Job, GitHub Actions CI Workflow, Logflare Analytics Service (+30 more)

### Community 5 - "Community 5"
Cohesion: 0.23
Nodes (13): GET(), baseCookieOptions, buildLoginPath(), buildRefreshPath(), clearAuthCookies(), isSafeRedirectTarget(), POST(), refreshFailedResponse() (+5 more)

### Community 8 - "Community 8"
Cohesion: 0.05
Nodes (8): NomadDispatchClient, OutboxMessageHandler, EventConsumerDedupeService, FairQueueDispatcherService, FifoJobDispatcherService, FifoJobDispatcherServiceTest, JobAdmissionWorker, OutboxConsumerDedupeService

### Community 10 - "Community 10"
Cohesion: 0.05
Nodes (37): dependencies, class-variance-authority, clsx, next, next-themes, postcss, radix-ui, react (+29 more)

### Community 11 - "Community 11"
Cohesion: 0.10
Nodes (5): SupabaseJwtAuthenticationConverter, getAuthorities(), IdempotencyRecordRepository, IdempotencyCleanupService, IdempotencyService

### Community 12 - "Community 12"
Cohesion: 0.17
Nodes (14): buildJobsHistoryHref(), clampPercentage(), getQuotaBarSegments(), HomeDashboard(), HomeDashboardProps, HomeDashboardSkeleton(), StatusCounts, Card() (+6 more)

### Community 13 - "Community 13"
Cohesion: 0.08
Nodes (9): IdempotencyConflictException, IdempotencyInProgressException, InsufficientQuotaException, InvalidIdempotencyKeyException, NomadDispatchException, NomadJobControlException, ResourceNotFoundException, NomadLogsUnavailableException (+1 more)

### Community 14 - "Community 14"
Cohesion: 0.17
Nodes (18): ArtifactDownloadResponse, BackendProblem, buildFileName(), GET(), Params, GET(), Params, getBackendUrlForServer() (+10 more)

### Community 15 - "Community 15"
Cohesion: 0.09
Nodes (21): aliases, components, hooks, lib, ui, utils, iconLibrary, menuAccent (+13 more)

### Community 16 - "Community 16"
Cohesion: 0.16
Nodes (17): buildJobsHref(), JobsHistoryView(), JobsHistoryViewProps, STATUS_OPTIONS, JobHistorySortDirection, JobsPageResponse, JobStatus, buildJobsPath() (+9 more)

### Community 17 - "Community 17"
Cohesion: 0.21
Nodes (12): JobHistoryItem, FAILED_STATUSES, fetchQuotaSummary(), fetchRecentJobs(), fetchStatusCount(), fetchStatusCounts(), Home(), JobsResult (+4 more)

### Community 18 - "Community 18"
Cohesion: 0.10
Nodes (19): compilerOptions, allowJs, esModuleInterop, incremental, isolatedModules, jsx, lib, module (+11 more)

### Community 19 - "Community 19"
Cohesion: 0.18
Nodes (11): Phase 1: Protect The Billing Invariant (Completed), Phase 2: Simplify Execution State (Completed), Phase 3: Unify Job And Run (Completed), Phase 4: Make Artifacts First-Class, Phase 4: Make Artifacts First-Class (Completed), Phase 5: Reduce Event Complexity, Phase 5: Reduce Event Complexity (Completed), Phase 6: Make Scheduling Boring (+3 more)

### Community 20 - "Community 20"
Cohesion: 0.07
Nodes (8): NomadLogsClient, JobLogChunkRepository, JobLogStreamRepository, from(), JobDetailsStreamService, JobLogCaptureService, JobLogsService, nomadValue()

### Community 21 - "Community 21"
Cohesion: 0.17
Nodes (17): AdminDashboardPage(), fetchCurrentUserRole(), fetchNodes(), getCountByStatus(), metadata, NodesResult, QuotaMeResponse, metadata (+9 more)

### Community 23 - "Community 23"
Cohesion: 0.13
Nodes (14): arrowParens, bracketSameLine, bracketSpacing, endOfLine, importOrder, jsxSingleQuote, plugins, printWidth (+6 more)

### Community 25 - "Community 25"
Cohesion: 0.06
Nodes (9): TestNomadConfig, NomadIntegrationConfig, NomadHttpJobControlClient, NomadHttpNodeClient, NomadJobControlClient, NomadNodeClient, NodeRepository, NodeQueryService (+1 more)

### Community 26 - "Community 26"
Cohesion: 0.14
Nodes (13): files, code, document, image, paper, video, graphifyignore_patterns, needs_graph (+5 more)

### Community 28 - "Community 28"
Cohesion: 0.12
Nodes (16): ACTIVE_STATUSES, ArtifactDownloadPayload, FieldRowProps, formatDateForUser(), JobDetailsPanel(), JobDetailsPanelProps, LogEventPayload, LogStream (+8 more)

### Community 29 - "Community 29"
Cohesion: 0.22
Nodes (8): code:bash (npm install), code:bash (cp .env.example .env), code:bash (docker compose up --build), Local service URLs, Private Cloud Resource Manager, Resetting the local stack (`reset.sh`), Run locally (Docker), Supabase Dashboard (Studio)

### Community 30 - "Community 30"
Cohesion: 0.33
Nodes (3): chunk(), heartbeat(), NomadLogsClient

### Community 31 - "Community 31"
Cohesion: 0.17
Nodes (15): ApiErrorResponse, BrokerFieldError, BrokerProblemDetail, buildFieldErrors(), envVarErrorMessages, isRecord(), JobSubmissionBody, JobSubmissionFieldErrors (+7 more)

### Community 33 - "Community 33"
Cohesion: 0.25
Nodes (7): devDependencies, husky, name, private, scripts, prepare, workspaces

### Community 34 - "Community 34"
Cohesion: 0.60
Nodes (3): AggregateSequenceId, EventConsumerDedupeId, Serializable

### Community 117 - "Community 117"
Cohesion: 0.14
Nodes (13): ThemeOption, DropdownMenu(), DropdownMenuCheckboxItem(), DropdownMenuContent(), DropdownMenuItem(), DropdownMenuLabel(), DropdownMenuRadioGroup(), DropdownMenuRadioItem() (+5 more)

### Community 119 - "Community 119"
Cohesion: 0.14
Nodes (22): FieldRowProps, formatDateForUser(), formatRamGb(), NodeDetailsPanel(), NodeDetailsProps, formatCountdown(), formatDateForUser(), formatHeartbeat() (+14 more)

### Community 120 - "Community 120"
Cohesion: 0.22
Nodes (10): ArtifactDownloadResponse, GET(), Params, GET(), Params, BackendProblem, BrokerAccessTokenResult, BrokerJsonProxyOptions (+2 more)

### Community 124 - "Community 124"
Cohesion: 0.20
Nodes (4): url, InternalStorageResource, missing(), StorageService

### Community 125 - "Community 125"
Cohesion: 0.14
Nodes (12): EnvVarsEditor(), EnvVarsEditorProps, JobCommandFields(), JobCommandFieldsProps, JobResourceFields(), JobResourceFieldsProps, LoginForm(), LoginPayload (+4 more)

### Community 127 - "Community 127"
Cohesion: 0.21
Nodes (13): AlertDialog(), AlertDialogAction(), AlertDialogCancel(), AlertDialogContent(), AlertDialogDescription(), AlertDialogFooter(), AlertDialogHeader(), AlertDialogMedia() (+5 more)

### Community 128 - "Community 128"
Cohesion: 0.25
Nodes (7): format, version, xl, distributionAlgo, sets, this, version

### Community 129 - "Community 129"
Cohesion: 0.29
Nodes (12): setAuthCookies(), isUserRole(), USER_ROLES, UserRole, POST(), QuotaMeResponse, GET(), getSafeNextPath() (+4 more)

### Community 131 - "Community 131"
Cohesion: 0.22
Nodes (11): collectEnvVarErrorMessages(), createEnvVarRow(), envVarFieldErrorMessages, EnvVarRow, toEnvVarFieldErrors(), toEnvVarsMap(), initialFormData, JobSubmissionFieldErrors (+3 more)

### Community 132 - "Community 132"
Cohesion: 0.06
Nodes (9): OutboxClaimRepository, AggregateSequenceService, DomainEventAppender, EventTopics, JobEventPublisher, JobOutboxPublisher, OutboxHandlerRegistry, OutboxPoller (+1 more)

### Community 133 - "Community 133"
Cohesion: 0.16
Nodes (11): ChunkEventPayload, ConnectionState, getConnectionBadgeClassName(), getConnectionBadgeLabel(), JobLogsPanel(), JobLogsPanelProps, JobLogsResponse, LoadState (+3 more)

### Community 134 - "Community 134"
Cohesion: 0.15
Nodes (11): NomadHttpDispatchClient, envVars, envVarsObj, error, isValidHybridJWT(), isValidJWT(), isValidLegacyJWT(), JWT_SECRET (+3 more)

### Community 135 - "Community 135"
Cohesion: 0.26
Nodes (9): HeaderProps, LogoutButton(), Breadcrumb(), BreadcrumbEllipsis(), BreadcrumbItem(), BreadcrumbLink(), BreadcrumbList(), BreadcrumbPage() (+1 more)

### Community 136 - "Community 136"
Cohesion: 0.29
Nodes (6): ibmPlexMono, ibmPlexSans, metadata, RootLayout(), Header(), ThemeProvider()

### Community 138 - "Community 138"
Cohesion: 0.18
Nodes (3): JobLogObjectStorage, InMemoryJobLogObjectStorage, S3JobLogObjectStorage

### Community 146 - "Community 146"
Cohesion: 0.22
Nodes (8): Backend Refactoring Findings, Bottom Line, code:sh (mkdir -p "$OUTPUT_DIR"), code:text (submit -> reserve lease -> dispatch -> run -> renew/kill -> ), Executive Summary, High-Value Tests To Add Later, Observability Recommendations, Recommended Product Contract For Users

### Community 149 - "Community 149"
Cohesion: 0.25
Nodes (8): 1. Build A Real Run State Machine, 2. Add Lease Renewal And Expiry Enforcement, 3. Make Artifact Finalization Durable, 4. Decide Log Retention Strategy, code:text (SUBMITTED), code:sh (mkdir -p "$NOMAD_ALLOC_DIR/data"), code:sh (mkdir -p "$OUTPUT_DIR"), Reliability Gaps To Address First

### Community 151 - "Community 151"
Cohesion: 0.29
Nodes (7): code:sql (job_artifacts), code:text (artifacts/<profile_id>/<job_id>/output.zip), Duplicated Mutable Fields, Missing Artifact State, Schema Findings, Tables That Are Core, Tables That May Be Overbuilt For Current Needs

### Community 153 - "Community 153"
Cohesion: 0.33
Nodes (6): Avoid Long-Running SSE Threads Per Client, Make Nomad Job ID Format One Thing, Reduce Quota Grant Complexity, Remove Submission-Specific Idempotency Fields From `jobs`, Replace `env_vars_json TEXT` With `JSONB`, Simplification Opportunities

### Community 154 - "Community 154"
Cohesion: 0.40
Nodes (5): Architecture Findings, Dispatch Fairness Is Probably Premature, Event-Driven Design Needs A Narrower Role, Missing Lease Enforcer, Node Table Is Mostly A Cache

### Community 158 - "Community 158"
Cohesion: 0.50
Nodes (4): Artifacts, Current Backend Flow, Job Submission, Logs

## Knowledge Gaps
- **253 isolated node(s):** `name`, `private`, `prepare`, `workspaces`, `husky` (+248 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **49 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `LogStream` connect `Community 133` to `Community 20`?**
  _High betweenness centrality (0.232) - this node is a cross-community bridge._
- **Why does `build` connect `Community 6` to `Community 0`, `Community 1`, `Community 130`, `Community 3`, `Community 132`, `Community 134`, `Community 8`, `Community 137`, `Community 10`, `Community 9`, `Community 138`, `Community 124`, `Community 148`, `Community 20`, `Community 25`, `Community 156`?**
  _High betweenness centrality (0.139) - this node is a cross-community bridge._
- **Why does `cn()` connect `Community 2` to `Community 135`, `Community 136`, `Community 10`, `Community 12`, `Community 117`, `Community 28`, `Community 125`, `Community 127`?**
  _High betweenness centrality (0.052) - this node is a cross-community bridge._
- **Are the 37 inferred relationships involving `build` (e.g. with `.queuedJob()` and `.node()`) actually correct?**
  _`build` has 37 INFERRED edges - model-reasoned connections that need verification._
- **Are the 24 inferred relationships involving `id` (e.g. with `.queuedJob()` and `.node()`) actually correct?**
  _`id` has 24 INFERRED edges - model-reasoned connections that need verification._
- **What connects `name`, `private`, `prepare` to the rest of the system?**
  _257 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.05348101265822785 - nodes in this community are weakly interconnected._