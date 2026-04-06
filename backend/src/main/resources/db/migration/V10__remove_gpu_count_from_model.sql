ALTER TABLE jobs
    DROP COLUMN IF EXISTS req_gpu_count;

ALTER TABLE nodes
    DROP COLUMN IF EXISTS total_gpu_count;
