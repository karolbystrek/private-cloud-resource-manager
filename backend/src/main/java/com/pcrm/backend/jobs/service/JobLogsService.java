package com.pcrm.backend.jobs.service;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobLogsResponse;
import com.pcrm.backend.jobs.repository.JobLogChunkRepository;
import com.pcrm.backend.jobs.repository.JobLogStreamRepository;
import com.pcrm.backend.nomad.NomadLogsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobLogsService {

    static final Set<JobStatus> TERMINAL_STATUSES = Set.of(
            JobStatus.SUCCEEDED,
            JobStatus.FAILED,
            JobStatus.CANCELED,
            JobStatus.TIMED_OUT,
            JobStatus.INFRA_FAILED
    );

    static final Set<JobStatus> ACTIVE_STATUSES = Set.of(
            JobStatus.SUBMITTED,
            JobStatus.QUEUED,
            JobStatus.DISPATCHING,
            JobStatus.SCHEDULING,
            JobStatus.RUNNING,
            JobStatus.FINALIZING
    );

    private final JobQueryService jobQueryService;
    private final JobLogStreamRepository logStreamRepository;
    private final JobLogChunkRepository logChunkRepository;
    private final JobLogObjectStorage logObjectStorage;

    @Transactional(readOnly = true)
    public JobLogsResponse getJobLogs(
            UUID jobId,
            CustomUserDetails principal,
            JobLogStreamType streamType,
            long limitBytes
    ) {
        jobQueryService.getJobDetails(jobId, principal);

        var stream = streamType.nomadValue();
        var streamState = logStreamRepository.findByJob_IdAndStream(jobId, stream);
        if (streamState.isEmpty()) {
            return new JobLogsResponse(
                    jobId,
                    stream,
                    "",
                    0L,
                    false,
                    TERMINAL_STATUSES.contains(jobQueryService.getJobDetails(jobId, principal).status()),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
        }

        var chunks = logChunkRepository.findByJob_IdAndStreamOrderBySequenceAsc(jobId, stream);
        long capturedBytes = chunks.stream()
                .mapToLong(chunk -> Math.max(0L, chunk.getSizeBytes()))
                .sum();
        long safeLimitBytes = Math.max(1L, limitBytes);
        long bytesToSkip = Math.max(0L, capturedBytes - safeLimitBytes);
        long seenBytes = 0L;
        var content = new StringBuilder();

        for (var chunk : chunks) {
            var text = logObjectStorage.getLogChunk(chunk.getObjectKey());
            var bytes = text.getBytes(StandardCharsets.UTF_8);
            long nextSeenBytes = seenBytes + bytes.length;
            if (nextSeenBytes <= bytesToSkip) {
                seenBytes = nextSeenBytes;
                continue;
            }

            if (bytesToSkip > seenBytes) {
                int start = Math.toIntExact(bytesToSkip - seenBytes);
                content.append(new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8));
            } else {
                content.append(text);
            }
            seenBytes = nextSeenBytes;
        }

        var state = streamState.get();
        return new JobLogsResponse(
                jobId,
                stream,
                content.toString(),
                capturedBytes,
                capturedBytes > safeLimitBytes,
                Boolean.TRUE.equals(state.getCaptureComplete()),
                state.getUpdatedAt()
        );
    }

    static boolean isTerminal(JobStatus status) {
        return TERMINAL_STATUSES.contains(status);
    }

    static boolean isActive(JobStatus status) {
        return ACTIVE_STATUSES.contains(status);
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
}
