package com.pcrm.backend.jobs.service;

import com.pcrm.backend.events.service.OutboxTopics;
import com.pcrm.backend.events.service.OutboxWriter;
import com.pcrm.backend.jobs.domain.Job;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobOutboxPublisher {

    private final OutboxWriter outboxWriter;

    @Transactional(propagation = Propagation.MANDATORY)
    public void jobSubmitted(Job job, String idempotencyKey, UUID correlationId) {
        outboxWriter.enqueue(
                OutboxTopics.JOB_SUBMITTED,
                Map.of(
                        "jobId", job.getId(),
                        "profileId", job.getProfile().getId()
                ),
                headers(correlationId, idempotencyKey)
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void jobQueued(Job job, UUID correlationId) {
        outboxWriter.enqueue(
                OutboxTopics.JOB_QUEUED,
                Map.of(
                        "jobId", job.getId(),
                        "profileId", job.getProfile().getId()
                ),
                headers(correlationId, null)
        );
    }

    private Map<String, ?> headers(UUID correlationId, String idempotencyKey) {
        var values = new java.util.LinkedHashMap<String, Object>();
        values.put("correlation_id", correlationId == null ? UUID.randomUUID() : correlationId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            values.put("idempotency_key", idempotencyKey);
        }
        return values;
    }
}
