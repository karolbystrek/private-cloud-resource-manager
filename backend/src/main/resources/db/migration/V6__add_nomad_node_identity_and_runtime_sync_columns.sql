ALTER TABLE nodes
    ADD COLUMN nomad_node_id UUID;

UPDATE nodes
SET nomad_node_id = id
WHERE nomad_node_id IS NULL;

ALTER TABLE nodes
    ALTER COLUMN nomad_node_id SET NOT NULL;

ALTER TABLE nodes
    ADD CONSTRAINT uq_nodes_nomad_node_id UNIQUE (nomad_node_id);

ALTER TABLE nodes
    DROP CONSTRAINT IF EXISTS nodes_hostname_key;

ALTER TABLE nodes
    ADD COLUMN nomad_status           VARCHAR(20),
    ADD COLUMN scheduling_eligibility VARCHAR(20);

CREATE INDEX idx_nodes_nomad_node_id ON nodes (nomad_node_id);
CREATE INDEX idx_nodes_status_last_heartbeat ON nodes (status, last_heartbeat);
