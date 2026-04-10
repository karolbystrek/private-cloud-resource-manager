package com.pcrm.backend.nodes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Node {

    @Id
    @Column(name = "id", nullable = false, length = 128)
    private String id;

    @Column(name = "nomad_node_id")
    private UUID nomadNodeId;

    @Column(nullable = false, length = 100)
    private String hostname;

    @Column(name = "ip_address", nullable = false)
    @ColumnTransformer(write = "CAST(? AS inet)")
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NodeStatus status;

    @Column(name = "nomad_status", length = 20)
    private String nomadStatus;

    @Column(name = "nomad_status_description")
    private String nomadStatusDescription;

    @Column(name = "scheduling_eligibility", length = 20)
    private String schedulingEligibility;

    @Column(name = "datacenter", length = 64)
    private String datacenter;

    @Column(name = "node_pool", length = 64)
    private String nodePool;

    @Column(name = "node_class", length = 64)
    private String nodeClass;

    @Column(name = "is_draining", nullable = false)
    private Boolean draining;

    @Column(name = "nomad_version", length = 50)
    private String nomadVersion;

    @Column(name = "docker_version", length = 50)
    private String dockerVersion;

    @Column(name = "nomad_create_index")
    private Long nomadCreateIndex;

    @Column(name = "nomad_modify_index")
    private Long nomadModifyIndex;

    @Column(name = "total_cpu_cores", nullable = false)
    private Integer totalCpuCores;

    @Column(name = "total_ram_mb", nullable = false)
    private Integer totalRamMb;

    @Column(name = "agent_version", nullable = false, length = 50)
    private String agentVersion;

    @Column(name = "last_heartbeat")
    private OffsetDateTime lastHeartbeat;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
