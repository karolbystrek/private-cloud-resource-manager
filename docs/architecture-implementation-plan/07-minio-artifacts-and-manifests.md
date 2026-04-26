# 07. MinIO Specs, Artifacts, and Final Manifests

## Goal

Promote MinIO from a single output ZIP store to the durable data plane for specs, inputs, outputs, logs archive, and final manifests.

A run may not become `SUCCEEDED` until:

```text
Nomad process succeeded
and final manifest exists in MinIO
and artifact metadata projection is updated
```

## Current Repository Baseline

- `StorageService` creates a bucket and generates presigned upload/download URLs.
- Artifact path is currently `artifacts/<user_id>/<job_id>/output.zip`.
- Frontend has artifact download routes and buttons.
- No final manifest model exists.

## Object Layout

Use per-run prefixes:

```text
jobs/<user_id>/<job_id>/<run_id>/spec/job-spec.json
jobs/<user_id>/<job_id>/<run_id>/inputs/...
jobs/<user_id>/<job_id>/<run_id>/outputs/...
jobs/<user_id>/<job_id>/<run_id>/logs/stdout.log
jobs/<user_id>/<job_id>/<run_id>/logs/stderr.log
jobs/<user_id>/<job_id>/<run_id>/manifest/final-manifest.json
jobs/<user_id>/<job_id>/<run_id>/archives/output.zip
```

Keep old artifact download URLs as compatibility wrappers that resolve the current run artifact.

## Final Manifest Contract

Manifest fields:

- `manifestVersion`
- `jobId`
- `runId`
- `userId`
- `status`
- `startedAt`
- `finishedAt`
- `resourceClass`
- `outputObjects`
- `logObjects`
- `archiveObjects`
- `totalBytes`
- `checksums`
- `runnerVersion`
- `correlationId`
- `createdAt`

The manifest proves outputs are finalized. It does not prove the run should be billed; quota remains control-plane responsibility.

## Database Migration

Add `artifacts_current`:

- `id UUID PRIMARY KEY`
- `job_id UUID NOT NULL`
- `run_id UUID NOT NULL`
- `user_id UUID NOT NULL`
- `manifest_object_key TEXT`
- `manifest_hash VARCHAR(128)`
- `artifact_prefix TEXT NOT NULL`
- `archive_object_key TEXT`
- `total_bytes BIGINT`
- `status VARCHAR(40) NOT NULL`
- `created_at`
- `updated_at`

Constraints:

- Unique `(run_id)`.
- Index `(user_id, updated_at DESC)`.

Add events:

- `JobSpecWritten`
- `ArtifactUploadStarted`
- `ArtifactObjectWritten`
- `ArtifactManifestWritten`
- `ArtifactFinalized`
- `ArtifactFinalizationMissing`
- `ArtifactManifestDiscovered`

## Backend Steps

1. Extend `StorageService`:
   - build per-run object keys
   - write job spec objects
   - check final manifest existence
   - read and validate manifest
   - generate scoped download URLs by run artifact key
2. Add `ArtifactService`:
   - validates manifest content
   - calculates/compares manifest hash
   - updates `artifacts_current`
   - appends artifact domain events
3. Add internal finalization endpoint:
   - authenticated for runner/internal use
   - idempotent by run ID + manifest hash
   - appends `ArtifactFinalized`
4. Update run finalization:
   - `RunProcessSucceeded` moves run to `FINALIZING`
   - `ArtifactFinalized` plus process success moves run to `SUCCEEDED`
5. Update download endpoints:
   - resolve job current run
   - verify artifact visibility and ownership
   - generate presigned GET for archive or selected artifact

## Runner Steps

1. Read `SPEC_URI`.
2. Execute workload.
3. Upload outputs under `OUTPUT_PREFIX`.
4. Optionally archive outputs to `archives/output.zip`.
5. Write `final-manifest.json` last.
6. Call internal finalization endpoint or rely on reconciler to discover the manifest.

The final manifest must be the last write because it declares outputs complete.

## Frontend Steps

- Job details should show artifacts only after `SUCCEEDED` or artifact projection status is finalized.
- If process succeeded but finalization is pending, show `FINALIZING`.
- If logs are unavailable after terminal state, provide artifact download if manifest exists.

## Tests

- Run cannot become `SUCCEEDED` without manifest.
- Finalization is idempotent for same manifest hash.
- Same finalization key with different manifest hash conflicts.
- Download endpoint refuses artifacts for non-owner.
- Reconciler can discover a manifest even if runner callback failed.

## Acceptance Criteria

- Every successful run has a durable final manifest.
- Artifact metadata is queryable without listing MinIO on every request.
- Existing download UX keeps working through compatibility resolution.
- Missing manifest leaves run in `FINALIZING` or repair state, never `SUCCEEDED`.

