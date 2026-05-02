package com.pcrm.backend.events.service;

import java.util.List;
import java.util.UUID;

public record DomainEventAppendRequest(
        String eventType,
        String aggregateType,
        String aggregateId,
        Object payload,
        String source,
        String actorType,
        UUID actorId,
        UUID userId,
        UUID jobId,
        UUID causationId,
        UUID correlationId,
        String idempotencyKey,
        List<String> topics
) {
}
