package com.pcrm.backend.jobs.service;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.jobs.domain.JobLogChunk;
import com.pcrm.backend.jobs.dto.JobDetailsResponse;
import com.pcrm.backend.jobs.repository.JobLogChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class JobDetailsStreamService {

    private final JobQueryService jobQueryService;
    private final JobLogChunkRepository logChunkRepository;
    private final JobLogObjectStorage logObjectStorage;

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${app.jobs.details-stream.interval-ms:2000}")
    private long intervalMs;

    @Value("${app.jobs.details-stream.timeout-ms:1800000}")
    private long timeoutMs;

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }

    public SseEmitter streamJobDetails(UUID jobId, CustomUserDetails principal) {
        var initial = jobQueryService.getJobDetails(jobId, principal);
        var emitter = new SseEmitter(Math.max(1L, timeoutMs));

        if (JobLogsService.isTerminal(initial.status())) {
            sendEvent(emitter, "job", initial);
            sendEvent(emitter, "end", new EndEvent("terminal"));
            emitter.complete();
            return emitter;
        }

        executorService.execute(() -> streamSnapshots(emitter, jobId, principal, initial));
        return emitter;
    }

    private void streamSnapshots(
            SseEmitter emitter,
            UUID jobId,
            CustomUserDetails principal,
            JobDetailsResponse initial
    ) {
        JobDetailsResponse lastSent = null;
        long lastStdoutSequence = -1L;
        long lastStderrSequence = -1L;
        try {
            var current = initial;
            while (true) {
                if (!Objects.equals(lastSent, current)) {
                    if (!sendEvent(emitter, "job", current)) {
                        return;
                    }
                    lastSent = current;
                }

                var nextStdoutSequence = sendLogChunks(emitter, jobId, JobLogStreamType.STDOUT, lastStdoutSequence);
                if (nextStdoutSequence == null) {
                    return;
                }
                lastStdoutSequence = nextStdoutSequence;

                var nextStderrSequence = sendLogChunks(emitter, jobId, JobLogStreamType.STDERR, lastStderrSequence);
                if (nextStderrSequence == null) {
                    return;
                }
                lastStderrSequence = nextStderrSequence;

                if (JobLogsService.isTerminal(current.status())) {
                    sendEvent(emitter, "end", new EndEvent("terminal"));
                    return;
                }

                Thread.sleep(Math.max(250L, intervalMs));
                current = jobQueryService.getJobDetails(jobId, principal);
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            sendEvent(emitter, "end", new EndEvent("error"));
        } finally {
            emitter.complete();
        }
    }

    private Long sendLogChunks(
            SseEmitter emitter,
            UUID jobId,
            JobLogStreamType streamType,
            long lastSequence
    ) {
        var chunks = logChunkRepository.findByJob_IdAndStreamAndSequenceGreaterThanOrderBySequenceAsc(
                jobId,
                streamType.nomadValue(),
                lastSequence
        );

        long nextSequence = lastSequence;
        for (var chunk : chunks) {
            var content = logObjectStorage.getLogChunk(chunk.getObjectKey());
            if (!sendEvent(emitter, "log", LogEvent.from(chunk, content))) {
                return null;
            }
            nextSequence = Math.max(nextSequence, chunk.getSequence());
        }
        return nextSequence;
    }

    private boolean sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
            return true;
        } catch (IOException | IllegalStateException closedStreamError) {
            return false;
        }
    }

    private record EndEvent(String reason) {
    }

    private record LogEvent(
            String stream,
            String data,
            long sequence,
            long offsetStart,
            long offsetEnd
    ) {
        static LogEvent from(JobLogChunk chunk, String data) {
            return new LogEvent(
                    chunk.getStream(),
                    data,
                    chunk.getSequence(),
                    chunk.getOffsetStart(),
                    chunk.getOffsetEnd()
            );
        }
    }
}
