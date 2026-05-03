package com.pcrm.backend.events.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DomainEventAppendRequest(
        String eventType,
        String aggregateType,
        String aggregateId,
        Object payload,
        Object metadata,
        String source,
        String actorType,
        String actorId,
        UUID userId,
        UUID jobId,
        UUID causationId,
        UUID correlationId,
        String idempotencyKey,
        OffsetDateTime occurredAt,
        Integer schemaVersion,
        List<String> topics
) {
}
