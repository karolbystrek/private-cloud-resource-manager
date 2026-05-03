package com.pcrm.backend.nomad.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.repository.RunRepository;
import com.pcrm.backend.jobs.service.JobRunEventPublisher;
import com.pcrm.backend.nodes.domain.Node;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.stream.dto.NomadEvent;
import com.pcrm.backend.nomad.stream.dto.NomadEventPayload;
import com.pcrm.backend.nomad.stream.dto.NomadEventStreamResponse;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
public class NomadEventStreamListener {

    private static final String META_NODE_ID_KEY = "private-cloud-resource-manager.node_id";

    private final String nomadBaseUrl;
    private final NomadStreamCursorRepository cursorRepository;
    private final JobRepository jobRepository;
    private final RunRepository runRepository;
    private final NodeRepository nodeRepository;
    private final QuotaAccountingService quotaAccountingService;
    private final JobRunEventPublisher eventPublisher;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    private volatile boolean running = true;
    private CompletableFuture<Void> streamFuture;

    public NomadEventStreamListener(
            String nomadBaseUrl,
            NomadStreamCursorRepository cursorRepository,
            JobRepository jobRepository,
            RunRepository runRepository,
            NodeRepository nodeRepository,
            QuotaAccountingService quotaAccountingService,
            JobRunEventPublisher eventPublisher,
            JsonMapper jsonMapper,
            TransactionTemplate transactionTemplate) {
        this.nomadBaseUrl = nomadBaseUrl;
        this.cursorRepository = cursorRepository;
        this.jobRepository = jobRepository;
        this.runRepository = runRepository;
        this.nodeRepository = nodeRepository;
        this.quotaAccountingService = quotaAccountingService;
        this.eventPublisher = eventPublisher;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = transactionTemplate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startListening() {
        // Runs in a separate thread so it doesn't block application startup
        executorService.submit(this::listenLoop);
    }

    @PreDestroy
    public void stopListening() {
        running = false;
        stopLatch.countDown();
        if (streamFuture != null) {
            streamFuture.cancel(true);
        }
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("NomadEventStreamListener executor did not terminate in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void listenLoop() {
        while (running) {
            try {
                Long lastIndex = getCursor();
                log.info("Connecting to Nomad Event Stream from index {}", lastIndex);

                String url = nomadBaseUrl + "/v1/event/stream?topic=Node&topic=Allocation&topic=Job&index=" + lastIndex;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMinutes(10)) // prevent hanging forever
                        .GET()
                        .build();

                streamFuture = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                        .thenAccept(this::processResponseStream);

                streamFuture.join(); // wait for the stream to finish or fail
            } catch (Exception e) {
                if (!running) break;
                log.error("Nomad stream connection failed or disconnected: {}", e.getMessage());
            }

            if (running) {
                try {
                    log.info("Reconnecting in 5 seconds...");
                    stopLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processResponseStream(HttpResponse<Stream<String>> response) {
        if (response.statusCode() != 200) {
            log.error("Nomad Event Stream returned status code {}", response.statusCode());
            return;
        }

        try (Stream<String> lines = response.body()) {
            lines.takeWhile(_ -> running)
                    .filter(line -> !line.isBlank() && !line.equals("{}")) // ignore simple heartbeats, handle empty events arrays safely below
                    .forEach(this::processEventLine);
        } catch (Exception e) {
            log.error("Error reading Nomad event stream lines", e);
        }
    }

    private void processEventLine(String line) {
        try {
            NomadEventStreamResponse streamResponse = jsonMapper.readValue(line, NomadEventStreamResponse.class);

            // Wrap the database updates for the event payloads AND the cursor update in a single @Transactional method.
            transactionTemplate.executeWithoutResult(_ -> {
                // Handle heartbeats gracefully: do not attempt to iterate over a null event array
                if (streamResponse != null && streamResponse.events() != null) {
                    for (NomadEvent event : streamResponse.events()) {
                        processEvent(event);
                    }
                }

                // Always update the cursor in the DB so we don't fall behind (even for heartbeats)
                if (streamResponse != null && streamResponse.index() != null) {
                    updateCursor(streamResponse.index());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Nomad Event JSON: {}", line, e);
        } catch (Exception e) {
            log.error("Unexpected error processing event line", e);
        }
    }

    protected void processEvent(NomadEvent event) {
        log.debug("Processing event {} - {}", event.topic(), event.type());
        if ("Node".equals(event.topic()) && event.payload() != null && event.payload().node() != null) {
            handleNodeEvent(event.payload().node());
        } else if ("Allocation".equals(event.topic()) && event.payload() != null && event.payload().allocation() != null) {
            handleAllocationEvent(event.payload().allocation());
        } else if ("Job".equals(event.topic()) && event.payload() != null && event.payload().job() != null) {
            handleJobEvent(event.payload().job(), event.type());
        }
    }

    private void handleNodeEvent(NomadEventPayload.NomadEventNode eventNode) {
        if (eventNode.id() == null) return;

        String customId = null;
        if (eventNode.meta() != null) {
            customId = eventNode.meta().get(META_NODE_ID_KEY);
        }

        Optional<Node> nodeOpt = Optional.empty();
        if (customId != null && !customId.isBlank()) {
            nodeOpt = nodeRepository.findById(customId);
        }

        if (nodeOpt.isEmpty()) {
            try {
                UUID nomadId = UUID.fromString(eventNode.id());
                nodeOpt = nodeRepository.findByNomadNodeId(nomadId);
            } catch (IllegalArgumentException ignored) {
            }
        }

        nodeOpt.ifPresent(node -> {
            boolean updated = false;
            if (eventNode.status() != null && !eventNode.status().equals(node.getNomadStatus())) {
                node.setNomadStatus(eventNode.status());
                updated = true;
            }
            if (eventNode.statusDescription() != null && !eventNode.statusDescription().equals(node.getNomadStatusDescription())) {
                node.setNomadStatusDescription(eventNode.statusDescription());
                updated = true;
            }
            if (eventNode.drain() != null && eventNode.drain() != node.getDraining()) {
                node.setDraining(eventNode.drain());
                updated = true;
            }
            if (eventNode.schedulingEligibility() != null && !eventNode.schedulingEligibility().equals(node.getSchedulingEligibility())) {
                node.setSchedulingEligibility(eventNode.schedulingEligibility());
                updated = true;
            }
            if (updated) {
                nodeRepository.save(node);
                log.info("Updated node {} from Nomad Event Stream", node.getId());
            }
        });
    }

    private void handleAllocationEvent(NomadEventPayload.NomadEventAllocation alloc) {
        resolveRun(alloc)
                .flatMap(run -> runRepository.findByIdForUpdate(run.getId()))
                .ifPresent(run -> {
                    var currentStatus = run.getStatus();
                    var newStatus = determineRunStatus(alloc, currentStatus);
                    boolean metadataUpdated = updateNomadAllocationMetadata(run, alloc);

                    if (currentStatus != newStatus) {
                        var now = OffsetDateTime.now(ZoneOffset.UTC);
                        run.setStatus(newStatus);
                        if (newStatus == RunStatus.RUNNING && run.getStartedAt() == null) {
                            run.setStartedAt(now);
                        }
                        if ((newStatus == RunStatus.FINALIZING || newStatus == RunStatus.SUCCEEDED) && run.getProcessFinishedAt() == null) {
                            run.setProcessFinishedAt(now);
                        }
                        if (newStatus == RunStatus.SUCCEEDED && run.getFinalizedAt() == null) {
                            run.setFinalizedAt(now);
                        }
                        var terminalReason = determineTerminalReason(alloc, newStatus);
                        if (terminalReason != null) {
                            run.setTerminalReason(terminalReason);
                        }
                        if (isTerminal(newStatus)) {
                            run.setProcessFinishedAt(now);
                            settleCurrentLeaseIfNeeded(run, now, "Lease settled after terminal Nomad allocation event");
                        }
                        runRepository.save(run);
                        syncJobProjection(run);
                        eventPublisher.runEvent(eventTypeForTransition(newStatus), run, nomadPayload(alloc), "nomad", UUID.randomUUID());
                        log.info("Updated run {} status from {} to {}", run.getId(), currentStatus, newStatus);
                        return;
                    }

                    if (metadataUpdated) {
                        runRepository.save(run);
                        syncJobProjection(run);
                    }
                });
    }

    private void handleJobEvent(NomadEventPayload.NomadEventJob eventJob, String eventType) {
        if (eventJob.id() == null) return;

        resolveRunByNomadJobId(eventJob.id())
                .flatMap(run -> runRepository.findByIdForUpdate(run.getId()))
                .ifPresent(run -> {
                    boolean updated = false;

                    if ("JobDeregistered".equals(eventType) || Boolean.TRUE.equals(eventJob.stop())) {
                        if (!isTerminal(run.getStatus()) && run.getStatus() != RunStatus.FINALIZING) {
                            var now = OffsetDateTime.now(ZoneOffset.UTC);
                            run.setStatus(RunStatus.CANCELED);
                            run.setProcessFinishedAt(now);
                            run.setTerminalReason("NOMAD_JOB_STOPPED");
                            settleCurrentLeaseIfNeeded(run, now, "Lease settled after Nomad stop event");
                            updated = true;
                        }
                    }

                    if (updated) {
                        runRepository.save(run);
                        syncJobProjection(run);
                        eventPublisher.runEvent("RunCanceled", run, Map.of("nomadEventType", eventType == null ? "" : eventType), "nomad", UUID.randomUUID());
                        log.info("Updated run {} to CANCELED from Nomad Job event", run.getId());
                    }
                });
    }

    private Optional<Run> resolveRun(NomadEventPayload.NomadEventAllocation alloc) {
        if (alloc.id() != null && !alloc.id().isBlank()) {
            var byAllocationId = runRepository.findByNomadAllocationId(alloc.id());
            if (byAllocationId.isPresent()) {
                return byAllocationId;
            }
        }

        if (alloc.jobId() == null || alloc.jobId().isBlank()) {
            return Optional.empty();
        }

        return resolveRunByNomadJobId(alloc.jobId());
    }

    private Optional<Run> resolveRunByNomadJobId(String nomadJobId) {
        var byNomadJobId = runRepository.findByNomadJobId(nomadJobId);
        if (byNomadJobId.isPresent()) {
            return byNomadJobId;
        }

        UUID runUuid = extractRunId(nomadJobId);
        if (runUuid != null) {
            return runRepository.findById(runUuid);
        }

        UUID jobUuid = extractJobId(nomadJobId);
        if (jobUuid != null) {
            return runRepository.findFirstByJob_IdOrderByRunNumberDesc(jobUuid);
        }

        return Optional.empty();
    }

    private UUID extractRunId(String nomadJobId) {
        if (nomadJobId == null) return null;
        try {
            if (nomadJobId.contains("-run#")) {
                String[] parts = nomadJobId.split("-run#");
                if (parts.length == 2) {
                    return UUID.fromString(parts[1]);
                }
            }
            return null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID extractJobId(String nomadJobId) {
        if (nomadJobId == null) return null;
        try {
            // Check for format user#<user-uuid>-job#<job-uuid>
            if (nomadJobId.contains("-job#")) {
                String[] parts = nomadJobId.split("-job#");
                if (parts.length == 2) {
                    if (parts[1].contains("-run#")) {
                        return UUID.fromString(parts[1].split("-run#")[0]);
                    }
                    return UUID.fromString(parts[1]);
                }
            }
            // Fallback for simple UUID
            return UUID.fromString(nomadJobId);
        } catch (IllegalArgumentException e) {
            return null; // Not our job format
        }
    }

    private boolean updateNomadAllocationMetadata(Run run, NomadEventPayload.NomadEventAllocation alloc) {
        boolean updated = false;
        if (alloc.id() != null && !alloc.id().isBlank() && !alloc.id().equals(run.getNomadAllocationId())) {
            run.setNomadAllocationId(alloc.id());
            updated = true;
        }
        if (alloc.evalId() != null && !alloc.evalId().isBlank() && !alloc.evalId().equals(run.getNomadEvalId())) {
            run.setNomadEvalId(alloc.evalId());
            updated = true;
        }
        if (alloc.jobId() != null && !alloc.jobId().isBlank() && !alloc.jobId().equals(run.getNomadJobId())) {
            run.setNomadJobId(alloc.jobId());
            updated = true;
        }
        return updated;
    }

    private RunStatus determineRunStatus(NomadEventPayload.NomadEventAllocation alloc, RunStatus current) {
        String clientStatus = alloc.clientStatus();
        if (clientStatus == null) return current;

        // Check if any task was OOM Killed
        if (isOomKilled(alloc)) {
            return RunStatus.FAILED;
        }

        return switch (clientStatus.toLowerCase()) {
            case "running" -> RunStatus.RUNNING;
            case "complete" -> RunStatus.SUCCEEDED;
            case "failed" -> isInfrastructureFailure(alloc) ? RunStatus.INFRA_FAILED : RunStatus.FAILED;
            case "pending" -> RunStatus.SCHEDULING;
            case "lost" -> RunStatus.INFRA_FAILED;
            case "unknown" -> current;
            case "dead" -> {
                if (alloc.taskStates() != null) {
                    for (Map.Entry<String, NomadEventPayload.TaskState> entry : alloc.taskStates().entrySet()) {
                        if (Boolean.TRUE.equals(entry.getValue().failed())) {
                            yield isInfrastructureFailure(alloc) ? RunStatus.INFRA_FAILED : RunStatus.FAILED;
                        }
                    }
                }
                yield RunStatus.SUCCEEDED;
            }
            default -> current;
        };
    }

    private String determineTerminalReason(NomadEventPayload.NomadEventAllocation alloc, RunStatus status) {
        if (isOomKilled(alloc)) {
            return "OOM_KILLED";
        }
        if (status == RunStatus.INFRA_FAILED) {
            var clientStatus = alloc.clientStatus();
            if ("lost".equalsIgnoreCase(clientStatus)) {
                return "NOMAD_ALLOCATION_LOST";
            }
            return "NOMAD_INFRA_FAILURE";
        }
        if (status == RunStatus.FAILED) {
            return "PROCESS_FAILED";
        }
        return null;
    }

    private boolean isOomKilled(NomadEventPayload.NomadEventAllocation alloc) {
        if (alloc.taskStates() == null) {
            return false;
        }
        for (Map.Entry<String, NomadEventPayload.TaskState> entry : alloc.taskStates().entrySet()) {
            NomadEventPayload.TaskState taskState = entry.getValue();
            if (Boolean.TRUE.equals(taskState.failed()) && taskState.events() != null) {
                for (NomadEventPayload.TaskEvent event : taskState.events()) {
                    if ("OOM Killed".equalsIgnoreCase(event.message())
                            || "OOM Killed".equalsIgnoreCase(event.displayMessage())
                            || "OOM".equalsIgnoreCase(event.type())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isInfrastructureFailure(NomadEventPayload.NomadEventAllocation alloc) {
        if (alloc.taskStates() == null) {
            return false;
        }
        for (NomadEventPayload.TaskState taskState : alloc.taskStates().values()) {
            if (taskState.events() == null) {
                continue;
            }
            for (NomadEventPayload.TaskEvent event : taskState.events()) {
                var type = event.type() == null ? "" : event.type().toLowerCase();
                if (type.contains("setup") || type.contains("driver") || type.contains("download")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTerminal(RunStatus status) {
        return status == RunStatus.SUCCEEDED
                || status == RunStatus.FAILED
                || status == RunStatus.CANCELED
                || status == RunStatus.TIMED_OUT
                || status == RunStatus.INFRA_FAILED;
    }

    private void settleCurrentLeaseIfNeeded(Run run, OffsetDateTime now, String reason) {
        if (Boolean.TRUE.equals(run.getLeaseSettled())) {
            return;
        }

        long reservedMinutes = Math.max(0L, run.getCurrentLeaseReservedMinutes());
        long consumedMinutes = calculateConsumedMinutes(run, now, reservedMinutes);

        quotaAccountingService.settleLeaseMinutes(run, reservedMinutes, consumedMinutes, reason);
        run.setTotalConsumedMinutes(run.getTotalConsumedMinutes() + consumedMinutes);
        run.setCurrentLeaseReservedMinutes(0L);
        run.setLeaseSettled(true);
        run.setActiveLeaseExpiresAt(null);
    }

    private long calculateConsumedMinutes(Run run, OffsetDateTime now, long reservedMinutes) {
        if (reservedMinutes <= 0 || run.getStartedAt() == null) {
            return 0L;
        }

        var leaseStart = run.getActiveLeaseExpiresAt() != null
                ? run.getActiveLeaseExpiresAt().minusMinutes(reservedMinutes)
                : run.getStartedAt();
        var effectiveStart = leaseStart.isAfter(run.getStartedAt()) ? leaseStart : run.getStartedAt();
        long elapsedSeconds = Math.max(0L, Duration.between(effectiveStart, now).getSeconds());
        long roundedUpMinutes = (elapsedSeconds + 59L) / 60L;
        return Math.min(reservedMinutes, roundedUpMinutes);
    }

    private void syncJobProjection(Run run) {
        var job = run.getJob();
        job.setStatus(run.getStatus());
        job.setCurrentRun(run);
        job.setStartedAt(run.getStartedAt());
        job.setFinishedAt(run.getProcessFinishedAt());
        job.setActiveLeaseExpiresAt(run.getActiveLeaseExpiresAt());
        job.setCurrentLeaseReservedMinutes(run.getCurrentLeaseReservedMinutes());
        job.setLeaseSequence(run.getLeaseSequence());
        job.setLeaseSettled(run.getLeaseSettled());
        job.setTotalConsumedMinutes(run.getTotalConsumedMinutes());
        jobRepository.save(job);
    }

    private String eventTypeForTransition(RunStatus status) {
        return switch (status) {
            case SUBMITTED -> "RunSubmitted";
            case RUNNING -> "RunStarted";
            case FINALIZING -> "RunFinalizing";
            case FAILED -> "RunFailed";
            case CANCELED -> "RunCanceled";
            case TIMED_OUT -> "RunTimedOut";
            case INFRA_FAILED -> "RunInfraFailed";
            case SUCCEEDED -> "RunSucceeded";
            case QUEUED -> "RunQueued";
            case DISPATCHING -> "RunDispatchRequested";
            case SCHEDULING -> "RunScheduled";
        };
    }

    private Map<String, Object> nomadPayload(NomadEventPayload.NomadEventAllocation alloc) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        if (alloc.id() != null) {
            payload.put("nomadAllocationId", alloc.id());
        }
        if (alloc.jobId() != null) {
            payload.put("nomadJobId", alloc.jobId());
        }
        if (alloc.evalId() != null) {
            payload.put("nomadEvalId", alloc.evalId());
        }
        if (alloc.clientStatus() != null) {
            payload.put("nomadClientStatus", alloc.clientStatus());
        }
        if (alloc.desiredStatus() != null) {
            payload.put("nomadDesiredStatus", alloc.desiredStatus());
        }
        return payload;
    }

    protected Long getCursor() {
        return transactionTemplate.execute(_ -> cursorRepository.findById(1)
                .map(NomadStreamCursor::getLastIndex)
                .orElse(0L));
    }

    protected void updateCursor(Long index) {
        NomadStreamCursor cursor = cursorRepository.findById(1).orElseGet(NomadStreamCursor::new);
        if (index > cursor.getLastIndex()) {
            cursor.setLastIndex(index);
            cursorRepository.save(cursor);
        }
    }
}
