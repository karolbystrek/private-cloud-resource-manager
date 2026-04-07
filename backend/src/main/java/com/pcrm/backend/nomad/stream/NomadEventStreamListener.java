package com.pcrm.backend.nomad.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nodes.domain.Node;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.stream.dto.NomadEvent;
import com.pcrm.backend.nomad.stream.dto.NomadEventPayload;
import com.pcrm.backend.nomad.stream.dto.NomadEventStreamResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
@Service
public class NomadEventStreamListener {

    private static final String META_NODE_ID_KEY = "private-cloud-resource-manager.node_id";

    private final String nomadBaseUrl;
    private final NomadStreamCursorRepository cursorRepository;
    private final JobRepository jobRepository;
    private final NodeRepository nodeRepository;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;

    private final HttpClient httpClient;
    private final ExecutorService executorService;
    private final CountDownLatch stopLatch = new CountDownLatch(1);

    private volatile boolean running = true;
    private CompletableFuture<Void> streamFuture;

    public NomadEventStreamListener(
            @Value("${app.nomad.base-url}") String nomadBaseUrl,
            NomadStreamCursorRepository cursorRepository,
            JobRepository jobRepository,
            NodeRepository nodeRepository,
            JsonMapper jsonMapper,
            TransactionTemplate transactionTemplate) {
        this.nomadBaseUrl = nomadBaseUrl;
        this.cursorRepository = cursorRepository;
        this.jobRepository = jobRepository;
        this.nodeRepository = nodeRepository;
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
        if (alloc.jobId() == null) return;

        UUID jobUuid = extractJobId(alloc.jobId());
        if (jobUuid == null) return;

        jobRepository.findById(jobUuid).ifPresent(job -> {
            JobStatus currentStatus = job.getStatus();
            JobStatus newStatus = determineJobStatus(alloc, currentStatus);

            if (currentStatus != newStatus) {
                job.setStatus(newStatus);
                jobRepository.save(job);
                log.info("Updated job {} status from {} to {}", jobUuid, currentStatus, newStatus);
            }
        });
    }

    private void handleJobEvent(NomadEventPayload.NomadEventJob eventJob, String eventType) {
        if (eventJob.id() == null) return;

        UUID jobUuid = extractJobId(eventJob.id());
        if (jobUuid == null) return;

        jobRepository.findById(jobUuid).ifPresent(job -> {
            boolean updated = false;

            if ("JobDeregistered".equals(eventType) || Boolean.TRUE.equals(eventJob.stop())) {
                if (job.getStatus() != JobStatus.COMPLETED && job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.OOM_KILLED) {
                    job.setStatus(JobStatus.STOPPED);
                    updated = true;
                }
            }

            if (updated) {
                jobRepository.save(job);
                log.info("Updated job {} to STOPPED from Nomad Job event", jobUuid);
            }
        });
    }

    private UUID extractJobId(String nomadJobId) {
        if (nomadJobId == null) return null;
        try {
            // Check for format user#<user-uuid>-job#<job-uuid>
            if (nomadJobId.contains("-job#")) {
                String[] parts = nomadJobId.split("-job#");
                if (parts.length == 2) {
                    return UUID.fromString(parts[1]);
                }
            }
            // Fallback for simple UUID
            return UUID.fromString(nomadJobId);
        } catch (IllegalArgumentException e) {
            return null; // Not our job format
        }
    }

    private JobStatus determineJobStatus(NomadEventPayload.NomadEventAllocation alloc, JobStatus current) {
        String clientStatus = alloc.clientStatus();
        if (clientStatus == null) return current;

        // Check if any task was OOM Killed
        if (alloc.taskStates() != null) {
            for (Map.Entry<String, NomadEventPayload.TaskState> entry : alloc.taskStates().entrySet()) {
                NomadEventPayload.TaskState taskState = entry.getValue();
                if (Boolean.TRUE.equals(taskState.failed()) && taskState.events() != null) {
                    for (NomadEventPayload.TaskEvent event : taskState.events()) {
                        if ("OOM Killed".equalsIgnoreCase(event.message()) || "OOM Killed".equalsIgnoreCase(event.displayMessage()) || "OOM".equalsIgnoreCase(event.type())) {
                            return JobStatus.OOM_KILLED;
                        }
                    }
                }
            }
        }

        return switch (clientStatus.toLowerCase()) {
            case "running" -> JobStatus.RUNNING;
            case "complete" -> JobStatus.COMPLETED;
            case "failed" -> JobStatus.FAILED;
            case "pending" -> JobStatus.PENDING;
            case "dead" -> {
                // Since OOM_KILLED is handled above, if we are here and any task failed, it's a general FAILED.
                // If no tasks failed explicitly, we consider it COMPLETED.
                if (alloc.taskStates() != null) {
                    for (Map.Entry<String, NomadEventPayload.TaskState> entry : alloc.taskStates().entrySet()) {
                        if (Boolean.TRUE.equals(entry.getValue().failed())) {
                            yield JobStatus.FAILED;
                        }
                    }
                }
                yield JobStatus.COMPLETED;
            }
            default -> current;
        };
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
