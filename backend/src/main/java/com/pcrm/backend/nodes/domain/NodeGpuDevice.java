package com.pcrm.backend.nodes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "node_gpu_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeGpuDevice {

    @Id
    private UUID id;

    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false, length = 40)
    private String vendor;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 120)
    private String model;

    @Column(name = "memory_mib")
    private Integer memoryMiB;

    @Column(length = 80)
    private String health;

    @Column(name = "driver_version", length = 80)
    private String driverVersion;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;
}
