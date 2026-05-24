ALTER TABLE jobs
    ADD COLUMN gpu_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN gpu_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN gpu_vendor VARCHAR(40),
    ADD COLUMN gpu_min_memory_gb INTEGER,
    ADD COLUMN gpu_model VARCHAR(120);

ALTER TABLE jobs
    ADD CONSTRAINT chk_jobs_gpu_count_nn CHECK (gpu_count >= 0),
    ADD CONSTRAINT chk_jobs_gpu_min_memory_positive CHECK (
        gpu_min_memory_gb IS NULL OR gpu_min_memory_gb >= 1
    ),
    ADD CONSTRAINT chk_jobs_gpu_requirement_consistent CHECK (
        (
            gpu_enabled = FALSE
            AND gpu_count = 0
            AND gpu_vendor IS NULL
            AND gpu_min_memory_gb IS NULL
            AND gpu_model IS NULL
        )
        OR
        (
            gpu_enabled = TRUE
            AND gpu_count >= 1
            AND gpu_vendor = 'nvidia'
        )
    );
