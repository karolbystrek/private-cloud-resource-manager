# AI Agent Guide: Private Cloud Resource Manager

## Repository

- GitHub: `karolbystrek/private-cloud-resource-manager`

## System In One View

- Mission: on-prem batch cloud with strict prepaid billing.
- Non-negotiable invariant: **No Unbilled Compute**.
- Control Plane: `backend/` (Java Spring Boot) handles API, auth, billing, and Nomad integration.
- Frontend: `frontend/` (Next.js + TypeScript + Shadcn UI).
- Execution Plane: Nomad schedules Docker jobs; lease enforcer must terminate jobs when lease expires and cannot be
  renewed.
- Storage/Infra: Supabase Postgres (incl. `auth.users`) + Supabase Storage (S3 API), Redis (distributed locks where used), Nomad (orchestration).

## Core Billing + Lease Rules (Must Never Break)

1. Job start deducts initial **15-minute lease** upfront.
2. Lease renewals extend execution in 15-minute chunks.
3. If renewal path fails (network partition / broker unreachable), job must be killed when current lease expires.
4. Wallet operations must be pessimistic and atomic:
    - lock wallet row with `SELECT ... FOR UPDATE`
    - never allow negative balance (`wallets.balance_credits >= 0`)
    - persist every credit move in append-only `credit_registry`
5. On failed dispatch or early completion, refund unused prepaid credits through ledger entry.

## Data + Concurrency Contracts

- PostgreSQL is final authority for balances and job state.
- Use row-level locks for wallet mutations; no read-modify-write without lock.
- Use Redis mutexes for concurrent lease/resource allocation paths to avoid thundering herd and double-reservation.
- Treat Broker as single authority for API contracts; frontend and lease enforcer are consumers.

## Auth Contract

- **Supabase Auth** (self-hosted GoTrue): users sign up / sign in via Supabase; JWT access token is issued by Auth.
- **Next.js** stores `access_token` and Supabase `refresh_token` as `HttpOnly` cookies and forwards `Authorization: Bearer <access_token>` to the broker.
- **Broker** validates Supabase JWTs with the shared `JWT_SECRET` (same value as GoTrue / `JWT_SECRET` in the repo-root `.env`).
- Domain role and billing: `profiles` table (`id` = `auth.users.id`) with wallets/jobs referencing `auth.users`.

## Repository Map (High Signal)

- `backend/`: Spring Boot broker/control plane.
- `backend/src/main/resources/db/migration/`: Flyway migrations (all schema changes go here).
- `frontend/`: Next.js app.
- `nomad/`: Nomad config + job template.
- `docs/`: project source of truth for architecture/roadmap.
- `docker-compose.yml`, `.env.example`, `reset.sh`: local stack at repo root.
- `volumes/`: bind-mounted Supabase config (Kong, Postgres init SQL, pooler, functions snippets, Storage, Vector, etc.).
- `scripts/supabase/`: optional Supabase ops helpers (`generate-keys.sh`, DB password change, etc.) and `smoke-test.sh`.

## Change Rules

- Database changes only through versioned Flyway migrations.
- Keep billing/lease logic strongly consistent before optimizing.
- Preserve append-only ledger semantics.
- Do not introduce paths that can execute compute without active prepaid lease.

## Frontend Rules

- Use Shadcn UI components for interactive/layout primitives.
- Use TypeScript `type` aliases (not `interface`).
- Keep modules small and SRP-focused; split files before they grow too large.
- Prefer Server Actions for mutate-then-refresh flows.
- Use `SubmitEvent`, not `FormEvent`.
- Keep code self-explanatory; avoid comments.

## Workflow Rules

- Branching default: work on `main`; push to `main` unless user says otherwise.
- Commits: Conventional Commits, atomic/single-purpose.
- Before commit: ask user for GitHub issue reference (`Ref: #<id>` if provided).
- Do not run `./mvnw test` or `npm run lint` (or any other build scripts) unless user explicitly asks.
