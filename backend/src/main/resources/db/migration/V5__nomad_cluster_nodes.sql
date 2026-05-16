CREATE TABLE nodes
(
    id                           VARCHAR(128) PRIMARY KEY,
    nomad_node_id                UUID         NOT NULL,
    hostname                     VARCHAR(100) NOT NULL,
    ip_address                   INET         NOT NULL,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    nomad_status                 VARCHAR(20),
    nomad_status_description     VARCHAR(255),
    scheduling_eligibility       VARCHAR(20),
    datacenter                   VARCHAR(64),
    node_pool                    VARCHAR(64),
    node_class                   VARCHAR(64),
    is_draining                  BOOLEAN      NOT NULL DEFAULT FALSE,
    nomad_version                VARCHAR(50),
    docker_version               VARCHAR(50),
    nomad_create_index           BIGINT,
    nomad_modify_index           BIGINT,
    total_cpu_cores              INTEGER      NOT NULL,
    total_ram_mb                 INTEGER      NOT NULL,
    agent_version                VARCHAR(50)  NOT NULL,
    last_heartbeat               TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ  DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nodes_status ON nodes (status);
CREATE INDEX idx_nodes_nomad_node_id ON nodes (nomad_node_id);
CREATE INDEX idx_nodes_status_last_heartbeat ON nodes (status, last_heartbeat);
