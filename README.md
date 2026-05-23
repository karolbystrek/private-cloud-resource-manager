# Private Cloud Resource Manager

On-prem batch cloud with prepaid lease billing; control plane is Spring Boot, execution is Nomad, identity and DB come
from self-hosted Supabase.

## Run locally (Docker)

1. **Install JavaScript dependencies** (monorepo workspace):

   ```bash
   npm install
   ```

2. **Configure env** — copy the template and edit if you need different ports or hosts (defaults are enough for local
   dev):

   ```bash
   cp .env.example .env
   ```

3. **Start everything** (from the repository root):

   ```bash
   docker compose up --build
   ```

## Resetting the local stack (`reset.sh`)

`reset.sh` tears down the Compose stack with volumes, can wipe bind-mounted Postgres and Storage data under `volumes/`,
and replaces `.env` from `.env.example` (after renaming the old file to `.env.old`). Use it when you want a clean local
install (corrupted or unwanted DB state, bad migration experiments, storage you do not need, or Compose left volumes in
a confusing state). It is destructive; do not run it if you care about data in those paths.

Run `./reset.sh` from the repo root and confirm each step, or `./reset.sh -y` to skip prompts. Afterwards, bring the
stack up again with `docker compose up --build` (and `docker compose pull` if you want newer images).

## Local service URLs

| Service                                        | URL (defaults)            |
|------------------------------------------------|---------------------------|
| Frontend                                       | http://localhost:3000     |
| Supabase Studio + REST/Auth API (Kong)         | http://localhost:8000     |
| Backend                                        | http://localhost:8080/api |
| Nomad                                          | http://localhost:4646     |
| MinIO S3 API                                   | http://localhost:9000     |
| MinIO Console                                  | http://localhost:9001     |

Optional HTTPS to the same gateway: `https://localhost:8443` when `KONG_HTTPS_PORT=8443`.

**First run:** sign up in the PCRM UI. Job artifacts are stored in MinIO; the Compose stack creates the bucket named by
`GLOBAL_S3_BUCKET` in your `.env`. To use admin APIs, set `profiles.role` to `ADMIN` for your user id (`auth.users.id`)
in Postgres.

## Supabase Dashboard (Studio)

Studio is served through Kong, not on its own port. **Use the public URL from your `.env`:** `SUPABASE_PUBLIC_URL` and
`API_EXTERNAL_URL` (defaults point at `http://localhost:8000`).

When the browser asks for HTTP Basic authentication, enter **`DASHBOARD_USERNAME`** and **`DASHBOARD_PASSWORD`** from
the same
`.env` file. Those values are the gateway credentials for Studio; they are not the email/password you use for Supabase
Auth in the app.
