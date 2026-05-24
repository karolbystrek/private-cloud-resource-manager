package com.pcrm.backend.nodes.repository;

import com.pcrm.backend.nodes.domain.NodeGpuDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NodeGpuDeviceRepository extends JpaRepository<NodeGpuDevice, UUID> {

    @Modifying
    void deleteByNodeId(String nodeId);

    List<NodeGpuDevice> findByNodeIdOrderByModelAscDeviceIdAsc(String nodeId);

    @Query(value = """
            SELECT
                d.node_id AS nodeId,
                n.hostname AS nodeHostname,
                d.vendor AS vendor,
                d.model AS model,
                MAX(d.memory_mib) AS maxMemoryMiB,
                COUNT(*) AS deviceCount
            FROM node_gpu_devices d
            JOIN nodes n ON n.id = d.node_id
            WHERE n.status = 'AVAILABLE'
              AND n.is_draining = FALSE
              AND COALESCE(LOWER(n.scheduling_eligibility), 'eligible') <> 'ineligible'
              AND d.vendor = 'nvidia'
              AND d.type = 'gpu'
            GROUP BY d.node_id, n.hostname, d.vendor, d.model
            ORDER BY n.hostname ASC, d.model ASC
            """, nativeQuery = true)
    List<GpuOptionRow> findAvailableGpuOptions();

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM (
                SELECT d.node_id
                FROM node_gpu_devices d
                JOIN nodes n ON n.id = d.node_id
                WHERE n.status = 'AVAILABLE'
                  AND n.is_draining = FALSE
                  AND COALESCE(LOWER(n.scheduling_eligibility), 'eligible') <> 'ineligible'
                  AND d.vendor = :vendor
                  AND d.type = 'gpu'
                  AND d.model = :model
                  AND (:minMemoryMiB IS NULL OR d.memory_mib >= :minMemoryMiB)
                GROUP BY d.node_id
                HAVING COUNT(*) >= :count
            ) eligible_node
            """, nativeQuery = true)
    boolean hasAvailableGpuCapacity(
            @Param("vendor") String vendor,
            @Param("model") String model,
            @Param("minMemoryMiB") Integer minMemoryMiB,
            @Param("count") int count
    );

    interface GpuOptionRow {
        String getNodeId();

        String getNodeHostname();

        String getVendor();

        String getModel();

        Integer getMaxMemoryMiB();

        Long getDeviceCount();
    }
}
