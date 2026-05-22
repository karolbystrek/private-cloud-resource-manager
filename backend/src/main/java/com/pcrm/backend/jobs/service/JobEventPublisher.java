package com.pcrm.backend.jobs.service;

import com.pcrm.backend.events.service.AggregateIds;
import com.pcrm.backend.events.service.DomainEventAppendRequest;
import com.pcrm.backend.events.service.DomainEventAppender;
import com.pcrm.backend.events.service.EventTopics;
import com.pcrm.backend.jobs.domain.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobEventPublisher {

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
                        "profileId", job.getProfile().getId(),
                        "dockerImage", job.getDockerImage(),
                        "reqCpuCores", job.getReqCpuCores(),
                        "reqRamGb", job.getReqRamGb()
                ),
                "USER",
                actorId,
                idempotencyKey,
                correlationId,
                List.of(EventTopics.JOB_SUBMITTED)
        );
    }

    public void jobEvent(String eventType, Job job, UUID correlationId) {
        jobEvent(eventType, job, Map.of(), SOURCE_BACKEND, correlationId);
    }

    public void jobEvent(String eventType, Job job, Map<String, ?> extraPayload, String source, UUID correlationId) {
        appendJobLifecycleEvent(eventType, job, extraPayload, source, correlationId, List.of(eventType));
    }

    private void appendJobLifecycleEvent(
            String eventType,
            Job job,
            Map<String, ?> extraPayload,
            String source,
            UUID correlationId,
            List<String> topics
    ) {
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("jobId", job.getId());
        payload.put("userId", job.getProfile().getId());
        payload.put("status", job.getStatus().name());
        if (job.getTerminalReason() != null) {
            payload.put("terminalReason", job.getTerminalReason());
        }
        payload.putAll(extraPayload);

        domainEventAppender.append(new DomainEventAppendRequest(
                eventType,
                AggregateIds.JOB,
                AggregateIds.job(job.getId()),
                payload,
                Map.of(),
                source,
                ACTOR_TYPE_SYSTEM,
                source.equals(SOURCE_NOMAD) ? "nomad-event-stream" : "backend",
                job.getProfile().getId(),
                job.getId(),
                null,
                correlationId == null ? UUID.randomUUID() : correlationId,
                null,
                OffsetDateTime.now(ZoneOffset.UTC),
                1,
                topics
        ));
    }

    private void appendJobEvent(
            String eventType,
            Job job,
            Object payload,
            String actorType,
            String actorId,
            String idempotencyKey,
            UUID correlationId,
            List<String> topics
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
                job.getProfile().getId(),
                job.getId(),
                null,
                correlationId == null ? UUID.randomUUID() : correlationId,
                idempotencyKey,
                OffsetDateTime.now(ZoneOffset.UTC),
                1,
                topics
        ));
    }
}
