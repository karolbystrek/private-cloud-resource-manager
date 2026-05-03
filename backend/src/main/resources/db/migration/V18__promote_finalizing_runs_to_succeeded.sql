UPDATE runs
SET status = 'SUCCEEDED',
    finalized_at = COALESCE(finalized_at, process_finished_at, now()),
    process_finished_at = COALESCE(process_finished_at, now()),
    updated_at = now()
WHERE status = 'FINALIZING';

UPDATE jobs
SET status = 'SUCCEEDED',
    finished_at = COALESCE(runs.process_finished_at, jobs.finished_at, now()),
    updated_at = now()
FROM runs
WHERE jobs.current_run_id = runs.id
  AND runs.status = 'SUCCEEDED'
  AND jobs.status = 'FINALIZING';
