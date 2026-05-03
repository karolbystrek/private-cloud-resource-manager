package com.pcrm.backend.events.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcrm.backend.events.domain.DomainEvent;
import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxMessageRepository outboxMessageRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(DomainEvent event, Collection<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return;
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var messages = new LinkedHashSet<>(topics).stream()
                .filter(topic -> topic != null && !topic.isBlank())
                .map(topic -> OutboxMessage.builder()
                        .id(UUID.randomUUID())
                        .eventId(event.getId())
                        .topic(topic)
                        .payload(event.getPayload())
                        .headers(headersFor(event))
                        .availableAt(now)
                        .createdAt(now)
                        .attemptCount(0)
                        .build())
                .toList();

        if (!messages.isEmpty()) {
            outboxMessageRepository.saveAll(messages);
        }
    }

    private JsonNode headersFor(DomainEvent event) {
        var headers = JsonNodeFactory.instance.objectNode();
        put(headers, "event_id", event.getId());
        put(headers, "event_type", event.getEventType());
        put(headers, "aggregate_type", event.getAggregateType());
        put(headers, "aggregate_id", event.getAggregateId());
        if (event.getSequenceNumber() != null) {
            headers.put("sequence_number", event.getSequenceNumber());
        }
        put(headers, "correlation_id", event.getCorrelationId());
        put(headers, "causation_id", event.getCausationId());
        if (event.getSchemaVersion() != null) {
            headers.put("schema_version", event.getSchemaVersion());
        }
        put(headers, "source", event.getSource());
        headers.put("content_type", "application/json");
        put(headers, "partition_key", event.getAggregateType() + ":" + event.getAggregateId());
        return headers;
    }

    private void put(ObjectNode headers, String fieldName, Object value) {
        if (value != null) {
            headers.put(fieldName, value.toString());
        }
    }
}
