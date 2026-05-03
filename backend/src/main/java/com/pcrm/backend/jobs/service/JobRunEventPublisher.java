package com.pcrm.backend.jobs.service;

import com.pcrm.backend.events.service.AggregateIds;
import com.pcrm.backend.events.service.DomainEventAppendRequest;
import com.pcrm.backend.events.service.DomainEventAppender;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobRunEventPublisher {

    private static final String SOURCE_BACKEND = "backend";
    private static final String SOURCE_NOMAD = "nomad";
    private static final String ACTOR_TYPE_SYSTEM = "SYSTEM";

    private final DomainEventAppender domainEventAppender;

    public void jobSubmitted(Job job, String actorId, String idempotencyKey, UUID correlationId) {
        appendJobEvent(
                "JobSubmitted",
                job,
                Map.of(
                        "jobId", job.getId(),
                        "userId", job.getUser().getId(),
                        "dockerImage", job.getDockerImage(),
                        "reqCpuCores", job.getReqCpuCores(),
                        "reqRamGb", job.getReqRamGb()
                ),
                "USER",
                actorId,
                idempotencyKey,
                correlationId
        );
    }

    public void runEvent(String eventType, Run run, UUID correlationId) {
        runEvent(eventType, run, Map.of(), SOURCE_BACKEND, correlationId);
    }

    public void runEvent(String eventType, Run run, Map<String, ?> extraPayload, String source, UUID correlationId) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("runId", run.getId());
        payload.put("jobId", run.getJob().getId());
        payload.put("userId", run.getUser().getId());
        payload.put("status", run.getStatus().name());
        payload.put("runNumber", run.getRunNumber());
        if (run.getNomadJobId() != null) {
            payload.put("nomadJobId", run.getNomadJobId());
        }
        if (run.getNomadAllocationId() != null) {
            payload.put("nomadAllocationId", run.getNomadAllocationId());
        }
        if (run.getTerminalReason() != null) {
            payload.put("terminalReason", run.getTerminalReason());
        }
        payload.putAll(extraPayload);

        domainEventAppender.append(new DomainEventAppendRequest(
                eventType,
                AggregateIds.RUN,
                AggregateIds.run(run.getId()),
                payload,
                Map.of(),
                source,
                ACTOR_TYPE_SYSTEM,
                source.equals(SOURCE_NOMAD) ? "nomad-event-stream" : "backend",
                run.getUser().getId(),
                run.getJob().getId(),
                null,
                correlationId == null ? UUID.randomUUID() : correlationId,
                null,
                OffsetDateTime.now(ZoneOffset.UTC),
                1,
                List.of(eventType)
        ));
    }

    private void appendJobEvent(
            String eventType,
            Job job,
            Object payload,
            String actorType,
            String actorId,
            String idempotencyKey,
            UUID correlationId
    ) {
        domainEventAppender.append(new DomainEventAppendRequest(
                eventType,
                AggregateIds.JOB,
                AggregateIds.job(job.getId()),
                payload,
                Map.of(),
                SOURCE_BACKEND,
                actorType,
                actorId,
                job.getUser().getId(),
                job.getId(),
                null,
                correlationId == null ? UUID.randomUUID() : correlationId,
                idempotencyKey,
                OffsetDateTime.now(ZoneOffset.UTC),
                1,
                List.of(eventType)
        ));
    }
}
