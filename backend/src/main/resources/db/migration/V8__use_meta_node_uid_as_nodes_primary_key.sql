ALTER TABLE jobs
    DROP CONSTRAINT IF EXISTS jobs_node_id_fkey;

ALTER TABLE nodes
    DROP CONSTRAINT IF EXISTS uq_nodes_nomad_node_id;

ALTER TABLE nodes
    ALTER COLUMN id TYPE VARCHAR(128) USING id::text;

ALTER TABLE jobs
    ALTER COLUMN node_id TYPE VARCHAR(128) USING node_id::text;

ALTER TABLE jobs
    ADD CONSTRAINT jobs_node_id_fkey
        FOREIGN KEY (node_id) REFERENCES nodes (id);

DROP INDEX IF EXISTS idx_nodes_nomad_node_id;
CREATE INDEX idx_nodes_nomad_node_id ON nodes (nomad_node_id);
