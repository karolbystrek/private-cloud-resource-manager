package com.pcrm.backend.nodes.service;

import com.pcrm.backend.nodes.domain.NodeGpuDevice;
import com.pcrm.backend.nodes.repository.NodeGpuDeviceRepository;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.NomadNodeClient;
import com.pcrm.backend.nomad.NomadNodeSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
public class NodeSyncService {

    private final NodeRepository nodeRepository;
    private final NodeGpuDeviceRepository nodeGpuDeviceRepository;
    private final NomadNodeClient nomadNodeClient;
    private final boolean syncEnabled;
    private final long staleTimeoutSec;
    private final TransactionTemplate transactionTemplate;


    public NodeSyncService(
            NodeRepository nodeRepository,
            NodeGpuDeviceRepository nodeGpuDeviceRepository,
            NomadNodeClient nomadNodeClient,
            PlatformTransactionManager transactionManager,
            @Value("${app.nomad.sync.enabled}") boolean syncEnabled,
            @Value("${app.nomad.sync.stale-timeout-sec}") long staleTimeoutSec
    ) {
        this.nodeRepository = nodeRepository;
        this.nodeGpuDeviceRepository = nodeGpuDeviceRepository;
        this.nomadNodeClient = nomadNodeClient;
        this.syncEnabled = syncEnabled;
        this.staleTimeoutSec = staleTimeoutSec;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        runReconciliation();
    }

    @Scheduled(fixedDelayString = "${app.nomad.sync.interval-ms}")
    public void syncPeriodically() {
        runReconciliation();
    }

    private void runReconciliation() {
        if (!syncEnabled) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        log.debug("Starting Nomad node synchronization");

        try {
            var snapshots = nomadNodeClient.fetchClientNodes();
            transactionTemplate.executeWithoutResult(_ -> {
                for (NomadNodeSnapshot snapshot : snapshots) {
                    upsertSnapshot(snapshot, now);
                }
                int staleRows = nodeRepository.markStaleAsOffline(now.minusSeconds(staleTimeoutSec));
                if (staleRows > 0) {
                    log.info("Marked {} stale nodes as OFFLINE", staleRows);
                }
            });
        } catch (Exception ex) {
            log.warn("Nomad node synchronization failed: {}", ex.getMessage());
        }
    }

    private void upsertSnapshot(NomadNodeSnapshot snapshot, OffsetDateTime now) {
        nodeRepository.upsertFromNomad(
                snapshot.id(),
                snapshot.nomadNodeId(),
                snapshot.hostname(),
                snapshot.ipAddress(),
                snapshot.status(),
                snapshot.statusDescription(),
                snapshot.schedulingEligibility(),
                snapshot.datacenter(),
                snapshot.nodePool(),
                snapshot.nodeClass(),
                snapshot.drain(),
                snapshot.nomadVersion(),
                snapshot.dockerVersion(),
                snapshot.nomadCreateIndex(),
                snapshot.nomadModifyIndex(),
                snapshot.totalCpuCores(),
                snapshot.totalRamMb(),
                snapshot.agentVersion(),
                now
        );
        nodeGpuDeviceRepository.deleteByNodeId(snapshot.id());
        var gpuDevices = snapshot.gpuDevices() == null
                ? java.util.List.<NodeGpuDevice>of()
                : snapshot.gpuDevices().stream()
                .map(device -> NodeGpuDevice.builder()
                        .id(UUID.randomUUID())
                        .nodeId(snapshot.id())
                        .deviceId(device.deviceId())
                        .vendor(device.vendor())
                        .type(device.type())
                        .model(device.model())
                        .memoryMiB(device.memoryMiB())
                        .health(device.health())
                        .driverVersion(device.driverVersion())
                        .lastSeenAt(now)
                        .build())
                .toList();
        nodeGpuDeviceRepository.saveAll(gpuDevices);
    }
}
