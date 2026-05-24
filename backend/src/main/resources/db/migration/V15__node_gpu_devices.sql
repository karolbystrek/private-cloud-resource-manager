CREATE TABLE node_gpu_devices
(
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    node_id        VARCHAR(128) NOT NULL REFERENCES nodes (id) ON DELETE CASCADE,
    device_id      VARCHAR(255) NOT NULL,
    vendor         VARCHAR(40)  NOT NULL,
    type           VARCHAR(40)  NOT NULL,
    model          VARCHAR(120) NOT NULL,
    memory_mib     INTEGER,
    health         VARCHAR(80),
    driver_version VARCHAR(80),
    last_seen_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_node_gpu_devices_memory_positive CHECK (memory_mib IS NULL OR memory_mib >= 1),
    CONSTRAINT uq_node_gpu_devices_node_device UNIQUE (node_id, device_id)
);

CREATE INDEX idx_node_gpu_devices_node_id ON node_gpu_devices (node_id);
CREATE INDEX idx_node_gpu_devices_vendor_model ON node_gpu_devices (vendor, model);
