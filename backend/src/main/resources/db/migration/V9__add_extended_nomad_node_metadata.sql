ALTER TABLE nodes
    ADD COLUMN nomad_status_description VARCHAR(255),
    ADD COLUMN datacenter VARCHAR(64),
    ADD COLUMN node_pool VARCHAR(64),
    ADD COLUMN node_class VARCHAR(64),
    ADD COLUMN is_draining BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN nomad_version VARCHAR(50),
    ADD COLUMN docker_version VARCHAR(50),
    ADD COLUMN nomad_create_index BIGINT,
    ADD COLUMN nomad_modify_index BIGINT;
