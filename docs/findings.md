# Findings

- `POST /api/jobs` now requires `envVars` to always be present in payload shape; clients must send `{}` when no variables are provided.
- `apps/frontend/src/app/api/jobs/route.ts` rejects missing or `null` `envVars` to stay consistent with backend `@NotNull` contract.
- Validation errors for environment variables flow through field-level keys prefixed with `envVars.` and must be preserved by frontend error mapping.
- `apps/broker/src/main/java/com/pcrm/broker/nomad/http/NomadHttpDispatchClient.java` avoids logging env var values because they may contain secrets.
- Environment variables are forwarded only at dispatch time and are not persisted in database job records.
