package com.pcrm.backend.idempotency.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;
import java.util.function.Function;

public record IdempotentWorkflow<T>(
        String actorType,
        String actorId,
        String workflow,
        String idempotencyKey,
        Object requestPayload,
        Function<IdempotencyContext, T> action,
        Function<JsonNode, T> completedResponseReader,
        String resourceType,
        Function<T, UUID> resourceId
) {
}
