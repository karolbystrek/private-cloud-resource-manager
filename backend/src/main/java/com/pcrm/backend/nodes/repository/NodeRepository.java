package com.pcrm.backend.nodes.repository;

import com.pcrm.backend.nodes.domain.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NodeRepository extends JpaRepository<Node, String> {

    List<Node> findAllByOrderByHostnameAsc();

    @Modifying
    @Query(value = """
            INSERT INTO nodes (
                id,
                nomad_node_id,
                hostname,
                ip_address,
                nomad_status,
                nomad_status_description,
                scheduling_eligibility,
                datacenter,
                node_pool,
                node_class,
                is_draining,
                nomad_version,
                docker_version,
                nomad_create_index,
                nomad_modify_index,
                total_cpu_cores,
                total_ram_mb,
                total_gpu_count,
                agent_version,
                last_heartbeat
            ) VALUES (
                :id,
                :nomadNodeId,
                :hostname,
                CAST(:ipAddress AS inet),
                :nomadStatus,
                :nomadStatusDescription,
                :schedulingEligibility,
                :datacenter,
                :nodePool,
                :nodeClass,
                :draining,
                :nomadVersion,
                :dockerVersion,
                :nomadCreateIndex,
                :nomadModifyIndex,
                :totalCpuCores,
                :totalRamMb,
                :totalGpuCount,
                :agentVersion,
                :lastHeartbeat
            )
            ON CONFLICT (id)
            DO UPDATE SET
                nomad_node_id = EXCLUDED.nomad_node_id,
                hostname = EXCLUDED.hostname,
                ip_address = EXCLUDED.ip_address,
                nomad_status = EXCLUDED.nomad_status,
                nomad_status_description = EXCLUDED.nomad_status_description,
                scheduling_eligibility = EXCLUDED.scheduling_eligibility,
                datacenter = EXCLUDED.datacenter,
                node_pool = EXCLUDED.node_pool,
                node_class = EXCLUDED.node_class,
                is_draining = EXCLUDED.is_draining,
                nomad_version = EXCLUDED.nomad_version,
                docker_version = EXCLUDED.docker_version,
                nomad_create_index = EXCLUDED.nomad_create_index,
                nomad_modify_index = EXCLUDED.nomad_modify_index,
                total_cpu_cores = EXCLUDED.total_cpu_cores,
                total_ram_mb = EXCLUDED.total_ram_mb,
                total_gpu_count = EXCLUDED.total_gpu_count,
                agent_version = EXCLUDED.agent_version,
                last_heartbeat = EXCLUDED.last_heartbeat
            """, nativeQuery = true)
    void upsertFromNomad(
            @Param("id") String id,
            @Param("nomadNodeId") UUID nomadNodeId,
            @Param("hostname") String hostname,
            @Param("ipAddress") String ipAddress,
            @Param("nomadStatus") String nomadStatus,
            @Param("nomadStatusDescription") String nomadStatusDescription,
            @Param("schedulingEligibility") String schedulingEligibility,
            @Param("datacenter") String datacenter,
            @Param("nodePool") String nodePool,
            @Param("nodeClass") String nodeClass,
            @Param("draining") boolean draining,
            @Param("nomadVersion") String nomadVersion,
            @Param("dockerVersion") String dockerVersion,
            @Param("nomadCreateIndex") Long nomadCreateIndex,
            @Param("nomadModifyIndex") Long nomadModifyIndex,
            @Param("totalCpuCores") int totalCpuCores,
            @Param("totalRamMb") int totalRamMb,
            @Param("totalGpuCount") int totalGpuCount,
            @Param("agentVersion") String agentVersion,
            @Param("lastHeartbeat") OffsetDateTime lastHeartbeat
    );

    @Modifying
    @Query(value = """
            UPDATE nodes
            SET status = 'OFFLINE'
            WHERE (last_heartbeat IS NULL OR last_heartbeat < :cutoff)
              AND status <> 'OFFLINE'
            """, nativeQuery = true)
    int markStaleAsOffline(@Param("cutoff") OffsetDateTime cutoff);
}
