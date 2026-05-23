# Graph Report - private-cloud-resource-manager  (2026-05-23)

## Corpus Check
- 272 files · ~49,385 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1209 nodes · 2010 edges · 132 communities (87 shown, 45 thin omitted)
- Extraction: 85% EXTRACTED · 15% INFERRED · 0% AMBIGUOUS · INFERRED: 305 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `a18d10bb`
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
- [[_COMMUNITY_Community 7|Community 7]]
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
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
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
- [[_COMMUNITY_Community 118|Community 118]]
- [[_COMMUNITY_Community 119|Community 119]]
- [[_COMMUNITY_Community 120|Community 120]]
- [[_COMMUNITY_Community 121|Community 121]]
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

## God Nodes (most connected - your core abstractions)
1. `cn()` - 66 edges
2. `build` - 31 edges
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

## Communities (132 total, 45 thin omitted)

### Community 1 - "Community 1"
Cohesion: 0.07
Nodes (10): QuotaReservation, start, status(), QuotaReservationRepository, UserQuotaBalanceCurrentRepository, UserQuotaOverrideRepository, AdminQuotaGrantService, LeaseWorkerTest (+2 more)

### Community 2 - "Community 2"
Cohesion: 0.15
Nodes (15): cn(), Avatar(), AvatarBadge(), AvatarFallback(), AvatarGroup(), AvatarGroupCount(), AvatarImage(), Separator() (+7 more)

### Community 3 - "Community 3"
Cohesion: 0.10
Nodes (7): NomadHttpLogsClient, chunk(), heartbeat(), NomadLogsClient, NomadLogsClient, JobLogsService, nomadValue()

### Community 4 - "Community 4"
Cohesion: 0.06
Nodes (52): Development Spring Profile, Production Spring Profile, H2 In-Memory Test Database, Test Spring Profile, Backend Base Configuration, Transactional Outbox Pattern, Flyway Database Migrations, Idempotency Key Cleanup (+44 more)

### Community 5 - "Community 5"
Cohesion: 0.05
Nodes (18): TestNomadConfig, NomadIntegrationConfig, NomadHttpDispatchClient, NomadHttpJobControlClient, NomadHttpNodeClient, envVars, envVarsObj, error (+10 more)

### Community 6 - "Community 6"
Cohesion: 0.18
Nodes (3): AggregateSequenceService, DomainEventAppender, EventTopics

### Community 7 - "Community 7"
Cohesion: 0.16
Nodes (10): JobCommandFields(), JobCommandFieldsProps, JobResourceFields(), JobResourceFieldsProps, LoginForm(), LoginPayload, SignupForm(), SignupPayload (+2 more)

### Community 8 - "Community 8"
Cohesion: 0.14
Nodes (3): JobRepository, ProfileRepository, JobQueryService

### Community 9 - "Community 9"
Cohesion: 0.16
Nodes (14): collectEnvVarErrorMessages(), createEnvVarRow(), EnvVarsEditor(), EnvVarsEditorProps, envVarFieldErrorMessages, EnvVarRow, toEnvVarFieldErrors(), toEnvVarsMap() (+6 more)

### Community 10 - "Community 10"
Cohesion: 0.05
Nodes (37): dependencies, class-variance-authority, clsx, next, next-themes, postcss, radix-ui, react (+29 more)

### Community 11 - "Community 11"
Cohesion: 0.09
Nodes (6): SupabaseJwtAuthenticationConverter, getAuthorities(), IdempotencyRecordRepository, IdempotencyCleanupService, IdempotencyService, ProfileProvisioningService

### Community 12 - "Community 12"
Cohesion: 0.10
Nodes (21): HomeDashboardSkeleton(), ChunkEventPayload, ConnectionState, getConnectionBadgeClassName(), getConnectionBadgeLabel(), JobLogsPanel(), JobLogsPanelProps, LogStream (+13 more)

### Community 13 - "Community 13"
Cohesion: 0.08
Nodes (9): IdempotencyConflictException, IdempotencyInProgressException, InsufficientQuotaException, InvalidIdempotencyKeyException, NomadDispatchException, NomadJobControlException, ResourceNotFoundException, NomadLogsUnavailableException (+1 more)

### Community 15 - "Community 15"
Cohesion: 0.09
Nodes (21): aliases, components, hooks, lib, ui, utils, iconLibrary, menuAccent (+13 more)

### Community 16 - "Community 16"
Cohesion: 0.29
Nodes (10): buildJobsPath(), JobsPage(), JobsPageSearchParams, metadata, parsePage(), parseSize(), parseSort(), parseStatuses() (+2 more)

### Community 17 - "Community 17"
Cohesion: 0.13
Nodes (22): buildJobsHistoryHref(), clampPercentage(), getQuotaBarSegments(), HomeDashboard(), HomeDashboardProps, StatusCounts, JobHistoryItem, JobHistorySortDirection (+14 more)

### Community 18 - "Community 18"
Cohesion: 0.10
Nodes (19): compilerOptions, allowJs, esModuleInterop, incremental, isolatedModules, jsx, lib, module (+11 more)

### Community 19 - "Community 19"
Cohesion: 0.04
Nodes (45): 1. Build A Real Run State Machine, 2. Add Lease Renewal And Expiry Enforcement, 3. Make Artifact Finalization Durable, 4. Decide Log Retention Strategy, Architecture Findings, Artifacts, Avoid Long-Running SSE Threads Per Client, Backend Refactoring Findings (+37 more)

### Community 20 - "Community 20"
Cohesion: 0.06
Nodes (48): ibmPlexMono, ibmPlexSans, metadata, RootLayout(), ArtifactDownloadResponse, BackendProblem, buildFileName(), GET() (+40 more)

### Community 21 - "Community 21"
Cohesion: 0.05
Nodes (14): JacksonConfig, JwtDecoderConfig, SecurityConfig, StorageConfig, build, url, JobArtifactRepository, QuotaPolicyRepository (+6 more)

### Community 22 - "Community 22"
Cohesion: 0.14
Nodes (13): ThemeOption, DropdownMenu(), DropdownMenuCheckboxItem(), DropdownMenuContent(), DropdownMenuItem(), DropdownMenuLabel(), DropdownMenuRadioGroup(), DropdownMenuRadioItem() (+5 more)

### Community 23 - "Community 23"
Cohesion: 0.13
Nodes (14): arrowParens, bracketSameLine, bracketSpacing, endOfLine, importOrder, jsxSingleQuote, plugins, printWidth (+6 more)

### Community 24 - "Community 24"
Cohesion: 0.13
Nodes (22): FieldRowProps, formatDateForUser(), formatRamGb(), NodeDetailsPanel(), NodeDetailsProps, formatCountdown(), formatDateForUser(), formatHeartbeat() (+14 more)

### Community 25 - "Community 25"
Cohesion: 0.27
Nodes (3): LeaseWorker, none(), stop()

### Community 26 - "Community 26"
Cohesion: 0.14
Nodes (13): files, code, document, image, paper, video, graphifyignore_patterns, needs_graph (+5 more)

### Community 28 - "Community 28"
Cohesion: 0.15
Nodes (15): ACTIVE_STATUSES, ArtifactDownloadPayload, FieldRowProps, formatDateForUser(), JobDetailsPanel(), JobDetailsPanelProps, JobDetails, JobDetailPage() (+7 more)

### Community 29 - "Community 29"
Cohesion: 0.22
Nodes (8): code:bash (npm install), code:bash (cp .env.example .env), code:bash (docker compose up --build), Local service URLs, Private Cloud Resource Manager, Resetting the local stack (`reset.sh`), Run locally (Docker), Supabase Dashboard (Studio)

### Community 30 - "Community 30"
Cohesion: 0.21
Nodes (13): GET(), baseCookieOptions, buildClearSessionPath(), buildLoginPath(), clearAuthCookies(), isSafeRedirectTarget(), POST(), refreshFailedResponse() (+5 more)

### Community 31 - "Community 31"
Cohesion: 0.21
Nodes (13): AlertDialog(), AlertDialogAction(), AlertDialogCancel(), AlertDialogContent(), AlertDialogDescription(), AlertDialogFooter(), AlertDialogHeader(), AlertDialogMedia() (+5 more)

### Community 32 - "Community 32"
Cohesion: 0.19
Nodes (3): OutboxMessageHandler, EventConsumerDedupeService, JobAdmissionWorker

### Community 33 - "Community 33"
Cohesion: 0.25
Nodes (7): devDependencies, husky, name, private, scripts, prepare, workspaces

### Community 34 - "Community 34"
Cohesion: 0.60
Nodes (3): AggregateSequenceId, EventConsumerDedupeId, Serializable

### Community 119 - "Community 119"
Cohesion: 0.16
Nodes (3): NodeRepository, NodeQueryService, NodeSyncService

### Community 121 - "Community 121"
Cohesion: 0.19
Nodes (4): JobSubmissionPersistenceService, JobSubmissionService, created(), replayed()

### Community 124 - "Community 124"
Cohesion: 0.26
Nodes (9): HeaderProps, LogoutButton(), Breadcrumb(), BreadcrumbEllipsis(), BreadcrumbItem(), BreadcrumbLink(), BreadcrumbList(), BreadcrumbPage() (+1 more)

### Community 125 - "Community 125"
Cohesion: 0.22
Nodes (3): id, QuotaUsageLedgerRepository, QuotaResource

### Community 127 - "Community 127"
Cohesion: 0.43
Nodes (7): AdminDashboardPage(), fetchNodes(), getCountByStatus(), metadata, NodesResult, brokerFetch(), getRequiredAccessToken()

### Community 128 - "Community 128"
Cohesion: 0.25
Nodes (7): format, version, xl, distributionAlgo, sets, this, version

## Knowledge Gaps
- **238 isolated node(s):** `name`, `private`, `prepare`, `workspaces`, `husky` (+233 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **45 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `cn()` connect `Community 2` to `Community 7`, `Community 10`, `Community 12`, `Community 124`, `Community 20`, `Community 22`, `Community 28`, `Community 31`?**
  _High betweenness centrality (0.246) - this node is a cross-community bridge._
- **Why does `scripts` connect `Community 10` to `Community 1`, `Community 21`?**
  _High betweenness centrality (0.242) - this node is a cross-community bridge._
- **Are the 30 inferred relationships involving `build` (e.g. with `.job()` and `.jsonMapper()`) actually correct?**
  _`build` has 30 INFERRED edges - model-reasoned connections that need verification._
- **What connects `name`, `private`, `prepare` to the rest of the system?**
  _243 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.11822660098522167 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.07168458781362007 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.14855072463768115 - nodes in this community are weakly interconnected._