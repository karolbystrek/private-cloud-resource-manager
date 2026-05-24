CREATE TABLE resource_weight_policy
(
    id                SMALLINT PRIMARY KEY,
    cpu_core_weight   INTEGER     NOT NULL DEFAULT 1,
    ram_gb_per_unit   INTEGER     NOT NULL DEFAULT 4,
    ram_unit_weight   INTEGER     NOT NULL DEFAULT 1,
    gpu_weight_tiers  JSONB       NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_resource_weight_policy_singleton CHECK (id = 1),
    CONSTRAINT chk_resource_weight_policy_cpu_pos CHECK (cpu_core_weight > 0),
    CONSTRAINT chk_resource_weight_policy_ram_gb_pos CHECK (ram_gb_per_unit > 0),
    CONSTRAINT chk_resource_weight_policy_ram_weight_pos CHECK (ram_unit_weight > 0)
);

INSERT INTO resource_weight_policy (
    id,
    cpu_core_weight,
    ram_gb_per_unit,
    ram_unit_weight,
    gpu_weight_tiers
)
VALUES (
    1,
    1,
    4,
    1,
    '[{"minMemoryGb":0,"weight":16},{"minMemoryGb":16,"weight":24},{"minMemoryGb":24,"weight":32},{"minMemoryGb":40,"weight":48}]'::jsonb
);

ALTER TABLE jobs
    ADD COLUMN quota_cpu_units BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN quota_ram_units BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN quota_gpu_units BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN quota_total_units BIGINT NOT NULL DEFAULT 0;

ALTER TABLE jobs
    ADD CONSTRAINT chk_jobs_quota_cpu_units_nn CHECK (quota_cpu_units >= 0),
    ADD CONSTRAINT chk_jobs_quota_ram_units_nn CHECK (quota_ram_units >= 0),
    ADD CONSTRAINT chk_jobs_quota_gpu_units_nn CHECK (quota_gpu_units >= 0),
    ADD CONSTRAINT chk_jobs_quota_total_units_nn CHECK (quota_total_units >= 0);
