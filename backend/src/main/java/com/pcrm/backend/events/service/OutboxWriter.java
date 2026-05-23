package com.pcrm.backend.events.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage enqueue(String topic, Object payload, Map<String, ?> headers) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic is required");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var message = OutboxMessage.builder()
                .id(UUID.randomUUID())
                .topic(topic)
                .payload(toJson(payload))
                .headers(headersToJson(headers))
                .availableAt(now)
                .createdAt(now)
                .attemptCount(0)
                .build();
        return outboxMessageRepository.save(message);
    }

    private JsonNode toJson(Object value) {
        if (value == null) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    private JsonNode headersToJson(Map<String, ?> headers) {
        var node = JsonNodeFactory.instance.objectNode();
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    node.put(key, value.toString());
                }
            });
        }
        node.put("content_type", "application/json");
        return node;
    }
}
