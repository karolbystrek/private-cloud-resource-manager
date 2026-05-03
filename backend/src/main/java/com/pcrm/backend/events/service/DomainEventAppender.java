package com.pcrm.backend.events.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.events.domain.DomainEvent;
import com.pcrm.backend.events.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DomainEventAppender {

    private final DomainEventRepository domainEventRepository;
    private final AggregateSequenceService aggregateSequenceService;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Transactional
    public DomainEvent append(DomainEventAppendRequest request) {
        validate(request);

        var sequenceNumber = aggregateSequenceService.allocateNext(request.aggregateType(), request.aggregateId());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var event = DomainEvent.builder()
                .id(UUID.randomUUID())
                .eventType(request.eventType())
                .aggregateType(request.aggregateType())
                .aggregateId(request.aggregateId())
                .sequenceNumber(sequenceNumber)
                .occurredAt(request.occurredAt() == null ? now : request.occurredAt())
                .createdAt(now)
                .schemaVersion(request.schemaVersion() == null ? 1 : request.schemaVersion())
                .actorType(blankToNull(request.actorType()))
                .actorId(blankToNull(request.actorId()))
                .userId(request.userId())
                .jobId(request.jobId())
                .causationId(request.causationId())
                .correlationId(request.correlationId())
                .idempotencyKey(blankToNull(request.idempotencyKey()))
                .source(request.source())
                .metadata(toJson(request.metadata()))
                .payload(toJson(request.payload()))
                .build();

        var savedEvent = domainEventRepository.save(event);
        outboxWriter.write(savedEvent, resolveTopics(request));
        return savedEvent;
    }

    private void validate(DomainEventAppendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Domain event append request is required");
        }
        requireText(request.eventType(), "eventType");
        requireText(request.aggregateType(), "aggregateType");
        requireText(request.aggregateId(), "aggregateId");
        requireText(request.source(), "source");
        if (request.correlationId() == null) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (request.schemaVersion() != null && request.schemaVersion() < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.valueToTree(value);
    }

    private List<String> resolveTopics(DomainEventAppendRequest request) {
        if (request.topics() == null || request.topics().isEmpty()) {
            return EventTopics.topicsForEventType(request.eventType());
        }
        return request.topics();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
