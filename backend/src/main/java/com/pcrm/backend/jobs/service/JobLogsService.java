package com.pcrm.backend.jobs.service;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.nomad.NomadLogsClient;
import com.pcrm.backend.nomad.NomadLogsUnavailableException;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogsService {

    private static final Set<JobStatus> TERMINAL_STATUSES = Set.of(
            JobStatus.COMPLETED,
            JobStatus.FAILED,
            JobStatus.OOM_KILLED,
            JobStatus.LEASE_EXPIRED,
            JobStatus.STOPPED
    );

    private final JobQueryService jobQueryService;
    private final JobRepository jobRepository;
    private final NomadLogsClient nomadLogsClient;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    @PreDestroy
    void shutdown() {
        streamExecutor.shutdownNow();
    }

    public SseEmitter streamJobLogs(
            UUID jobId,
            CustomUserDetails principal,
            JobLogStreamType streamType,
            long initialOffset
    ) {
        var jobDetails = jobQueryService.getJobDetails(jobId, principal);
        boolean follow = !TERMINAL_STATUSES.contains(jobDetails.status());
        String nomadJobId = buildNomadJobId(jobDetails.userId(), jobId);

        SseEmitter emitter = new SseEmitter(0L);
        streamExecutor.execute(() -> streamToClient(
                emitter,
                jobId,
                nomadJobId,
                streamType,
                follow,
                initialOffset
        ));
        return emitter;
    }

    private void streamToClient(
            SseEmitter emitter,
            UUID jobId,
            String nomadJobId,
            JobLogStreamType streamType,
            boolean follow,
            long initialOffset
    ) {
        AtomicLong lastOffset = new AtomicLong(Math.max(0L, initialOffset));
        try {
            if (!sendEvent(emitter, "meta", new MetaEvent(
                    jobId,
                    nomadJobId,
                    streamType.nomadValue(),
                    follow,
                    lastOffset.get(),
                    OffsetDateTime.now()
            ))) {
                return;
            }

            if (!sendEvent(emitter, "status", new StatusEvent(
                    "resolving_allocation",
                    true,
                    "Resolving Nomad allocation for this job."
            ))) {
                return;
            }

            var allocations = nomadLogsClient.listJobAllocations(nomadJobId);
            var selectedAllocation = selectRelevantAllocation(allocations, follow);
            if (selectedAllocation.isEmpty()) {
                if (follow) {
                    sendEvent(emitter, "status", new StatusEvent(
                            "waiting_allocation",
                            true,
                            "No allocation is available yet. Reconnect to retry."
                    ));
                    sendEvent(emitter, "end", new EndEvent("allocation_pending", lastOffset.get(), OffsetDateTime.now()));
                } else {
                    sendEvent(emitter, "unavailable", new UnavailableEvent(
                            "allocation_missing",
                            "Logs are unavailable for this completed job.",
                            streamType.nomadValue()
                    ));
                    sendEvent(emitter, "end", new EndEvent("unavailable", lastOffset.get(), OffsetDateTime.now()));
                }
                return;
            }

            var allocation = selectedAllocation.get();
            if (!sendEvent(emitter, "status", new StatusEvent(
                    "streaming",
                    follow,
                    "Streaming logs."
            ))) {
                return;
            }

            nomadLogsClient.streamAllocationLogs(
                    allocation.id(),
                    streamType.nomadValue(),
                    follow,
                    lastOffset.get(),
                    frame -> handleFrame(emitter, frame, streamType, lastOffset)
            );

            sendEvent(emitter, "end", new EndEvent("stream_closed", lastOffset.get(), OffsetDateTime.now()));
        } catch (NomadLogsUnavailableException unavailableException) {
            if (follow && !isTerminalInRepository(jobId)) {
                sendEvent(emitter, "status", new StatusEvent(
                        "stream_unavailable",
                        true,
                        "Logs are not available yet. Reconnect to retry."
                ));
                sendEvent(emitter, "end", new EndEvent("retry", lastOffset.get(), OffsetDateTime.now()));
            } else {
                sendEvent(emitter, "unavailable", new UnavailableEvent(
                        "retention_expired",
                        "Logs are no longer available for this finished job.",
                        streamType.nomadValue()
                ));
                sendEvent(emitter, "end", new EndEvent("unavailable", lastOffset.get(), OffsetDateTime.now()));
            }
        } catch (Exception exception) {
            log.warn("Failed to stream logs for job {}: {}", jobId, exception.getMessage());
            sendEvent(emitter, "status", new StatusEvent(
                    "stream_error",
                    follow,
                    "Log stream interrupted. Reconnect to continue."
            ));
            sendEvent(emitter, "end", new EndEvent("error", lastOffset.get(), OffsetDateTime.now()));
        } finally {
            emitter.complete();
        }
    }

    private void handleFrame(
            SseEmitter emitter,
            NomadLogsClient.NomadLogFrame frame,
            JobLogStreamType streamType,
            AtomicLong lastOffset
    ) {
        if (frame.type() == NomadLogsClient.NomadLogFrameType.CHUNK && frame.chunk() != null) {
            long nextOffset = resolveNextOffset(lastOffset.get(), frame.offset(), frame.byteLength());
            lastOffset.set(nextOffset);
            sendEvent(emitter, "chunk", new ChunkEvent(streamType.nomadValue(), frame.chunk(), nextOffset));
            return;
        }

        if (frame.type() == NomadLogsClient.NomadLogFrameType.STATUS && frame.message() != null) {
            sendEvent(emitter, "status", new StatusEvent("nomad_status", true, frame.message()));
            return;
        }

        sendEvent(emitter, "heartbeat", new HeartbeatEvent(lastOffset.get(), OffsetDateTime.now()));
    }

    private boolean isTerminalInRepository(UUID jobId) {
        return jobRepository.findById(jobId)
                .map(job -> TERMINAL_STATUSES.contains(job.getStatus()))
                .orElse(true);
    }

    static java.util.Optional<NomadLogsClient.NomadAllocationSnapshot> selectRelevantAllocation(
            List<NomadLogsClient.NomadAllocationSnapshot> allocations,
            boolean follow
    ) {
        Comparator<NomadLogsClient.NomadAllocationSnapshot> comparator = Comparator
                .comparingInt((NomadLogsClient.NomadAllocationSnapshot allocation) -> scoreAllocation(allocation, follow))
                .thenComparingLong(NomadLogsClient.NomadAllocationSnapshot::modifyIndex)
                .thenComparingLong(NomadLogsClient.NomadAllocationSnapshot::createIndex)
                .thenComparing(NomadLogsClient.NomadAllocationSnapshot::id);

        return allocations.stream().max(comparator);
    }

    private static int scoreAllocation(NomadLogsClient.NomadAllocationSnapshot allocation, boolean follow) {
        if (allocation.clientStatus() == null) {
            return follow ? 10 : 20;
        }

        String normalized = allocation.clientStatus().toLowerCase(Locale.ROOT);
        if (follow) {
            return switch (normalized) {
                case "running" -> 500;
                case "pending", "starting" -> 400;
                case "failed", "complete", "dead", "lost" -> 200;
                default -> 100;
            };
        }

        return switch (normalized) {
            case "complete", "failed", "dead", "lost" -> 500;
            case "running" -> 300;
            case "pending", "starting" -> 200;
            default -> 100;
        };
    }

    private static long resolveNextOffset(long previousOffset, long frameOffset, int byteLength) {
        long baseOffset = frameOffset >= 0 ? frameOffset : previousOffset;
        long positiveLength = Math.max(0, byteLength);
        return baseOffset + positiveLength;
    }

    private String buildNomadJobId(UUID ownerId, UUID jobId) {
        return "user#" + ownerId + "-job#" + jobId;
    }

    private boolean sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (IOException | IllegalStateException closedStreamError) {
            return false;
        }
    }

    private record MetaEvent(
            UUID jobId,
            String nomadJobId,
            String stream,
            boolean follow,
            long offset,
            OffsetDateTime connectedAt
    ) {
    }

    private record ChunkEvent(String stream, String data, long offset) {
    }

    private record StatusEvent(String state, boolean retryable, String message) {
    }

    private record UnavailableEvent(String reason, String message, String stream) {
    }

    private record EndEvent(String reason, long offset, OffsetDateTime endedAt) {
    }

    private record HeartbeatEvent(long offset, OffsetDateTime timestamp) {
    }
}
