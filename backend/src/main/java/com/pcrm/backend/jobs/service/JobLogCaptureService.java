package com.pcrm.backend.jobs.service;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobLogChunk;
import com.pcrm.backend.jobs.domain.JobLogStream;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobLogChunkRepository;
import com.pcrm.backend.jobs.repository.JobLogStreamRepository;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nomad.NomadLogsClient;
import com.pcrm.backend.nomad.NomadLogsUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.logs.capture.enabled", havingValue = "true", matchIfMissing = true)
public class JobLogCaptureService {

    private static final JobLogStreamType[] STREAM_TYPES = {
            JobLogStreamType.STDOUT,
            JobLogStreamType.STDERR
    };

    private static final EnumSet<JobStatus> CAPTURE_CANDIDATE_STATUSES = EnumSet.of(
            JobStatus.DISPATCHING,
            JobStatus.SCHEDULING,
            JobStatus.RUNNING,
            JobStatus.FINALIZING,
            JobStatus.SUCCEEDED,
            JobStatus.FAILED,
            JobStatus.CANCELED,
            JobStatus.TIMED_OUT,
            JobStatus.INFRA_FAILED
    );

    private final JobRepository jobRepository;
    private final JobLogStreamRepository logStreamRepository;
    private final JobLogChunkRepository logChunkRepository;
    private final NomadLogsClient nomadLogsClient;
    private final JobLogObjectStorage logObjectStorage;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.logs.capture.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.logs.capture.interval-ms:5000}")
    public void captureJobLogs() {
        if (batchSize <= 0) {
            return;
        }

        jobRepository.findTop100ByStatusInOrderByUpdatedAtDesc(CAPTURE_CANDIDATE_STATUSES)
                .stream()
                .limit(batchSize)
                .map(Job::getId)
                .forEach(this::captureJob);
    }

    private void captureJob(UUID jobId) {
        for (var streamType : STREAM_TYPES) {
            captureStream(jobId, streamType);
        }
    }

    private void captureStream(UUID jobId, JobLogStreamType streamType) {
        var streamState = transactionTemplate.execute(_ -> ensureStream(jobId, streamType));
        if (streamState == null || Boolean.TRUE.equals(streamState.getCaptureComplete())) {
            return;
        }

        var job = streamState.getJob();
        boolean terminal = JobLogsService.isTerminal(job.getStatus());
        try {
            var allocations = nomadLogsClient.listJobAllocations(job.getId().toString());
            var allocation = JobLogsService.selectRelevantAllocation(allocations, !terminal);
            if (allocation.isEmpty()) {
                if (terminal) {
                    transactionTemplate.executeWithoutResult(_ -> markComplete(jobId, streamType));
                }
                return;
            }

            var nextOffset = new AtomicLong(Math.max(0L, streamState.getLastOffset()));
            var wroteChunk = new AtomicBoolean(false);
            nomadLogsClient.streamAllocationLogs(
                    allocation.get().id(),
                    streamType.nomadValue(),
                    false,
                    nextOffset.get(),
                    frame -> {
                        if (frame.type() != NomadLogsClient.NomadLogFrameType.CHUNK || frame.chunk() == null || frame.chunk().isEmpty()) {
                            return;
                        }
                        var startOffset = Math.max(0L, frame.offset() >= 0L ? frame.offset() : nextOffset.get());
                        var sizeBytes = frame.chunk().getBytes(StandardCharsets.UTF_8).length;
                        var endOffset = startOffset + sizeBytes;
                        transactionTemplate.executeWithoutResult(_ ->
                                persistChunk(jobId, streamType, frame.chunk(), startOffset, endOffset, sizeBytes)
                        );
                        nextOffset.set(endOffset);
                        wroteChunk.set(true);
                    }
            );

            if (terminal && !wroteChunk.get()) {
                transactionTemplate.executeWithoutResult(_ -> markComplete(jobId, streamType));
            }
        } catch (NomadLogsUnavailableException unavailableException) {
            if (terminal) {
                transactionTemplate.executeWithoutResult(_ -> markComplete(jobId, streamType));
            } else {
                transactionTemplate.executeWithoutResult(_ -> recordError(jobId, streamType, "Logs are not available yet."));
            }
        } catch (Exception ex) {
            log.debug("Failed to capture {} logs for job {}: {}", streamType.nomadValue(), jobId, ex.getMessage());
            transactionTemplate.executeWithoutResult(_ -> recordError(jobId, streamType, summarize(ex)));
        }
    }

    private JobLogStream ensureStream(UUID jobId, JobLogStreamType streamType) {
        var stream = streamType.nomadValue();
        return logStreamRepository.findByJobIdAndStreamForUpdate(jobId, stream)
                .orElseGet(() -> {
                    var job = jobRepository.findWithProfileById(jobId).orElseThrow();
                    return logStreamRepository.save(JobLogStream.builder()
                            .job(job)
                            .profile(job.getProfile())
                            .stream(stream)
                            .lastOffset(0L)
                            .captureComplete(false)
                            .build());
                });
    }

    private void persistChunk(
            UUID jobId,
            JobLogStreamType streamType,
            String content,
            long offsetStart,
            long offsetEnd,
            long sizeBytes
    ) {
        var streamState = ensureStream(jobId, streamType);
        if (offsetEnd <= Math.max(0L, streamState.getLastOffset())) {
            return;
        }

        var sequence = logChunkRepository.findLatestForStream(
                        streamState.getId(),
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .map(chunk -> chunk.getSequence() + 1L)
                .orElse(0L);

        var objectKey = buildLogObjectKey(
                streamState.getProfile().getId(),
                streamState.getJob().getId(),
                streamType.nomadValue(),
                sequence
        );
        logObjectStorage.putLogChunk(objectKey, content);

        logChunkRepository.save(JobLogChunk.builder()
                .logStream(streamState)
                .job(streamState.getJob())
                .profile(streamState.getProfile())
                .stream(streamType.nomadValue())
                .sequence(sequence)
                .objectKey(objectKey)
                .offsetStart(offsetStart)
                .offsetEnd(offsetEnd)
                .sizeBytes(sizeBytes)
                .build());

        streamState.setLastOffset(offsetEnd);
        streamState.setLastError(null);
        streamState.setCaptureComplete(false);
        logStreamRepository.save(streamState);
    }

    private void markComplete(UUID jobId, JobLogStreamType streamType) {
        var streamState = ensureStream(jobId, streamType);
        streamState.setCaptureComplete(true);
        streamState.setLastError(null);
        logStreamRepository.save(streamState);
    }

    private void recordError(UUID jobId, JobLogStreamType streamType, String reason) {
        var streamState = ensureStream(jobId, streamType);
        streamState.setLastError(reason);
        logStreamRepository.save(streamState);
    }

    private String buildLogObjectKey(UUID userId, UUID jobId, String stream, long sequence) {
        return "logs/" + userId + "/" + jobId + "/" + stream + "/" + sequence + ".log";
    }

    private String summarize(Exception ex) {
        var message = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
        return message.length() <= 4000 ? message : message.substring(0, 4000);
    }
}
