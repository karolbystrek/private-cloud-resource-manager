package com.pcrm.backend.events.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OutboxHandlerRegistry {

    private final Map<String, OutboxMessageHandler> handlers;

    public OutboxHandlerRegistry(List<OutboxMessageHandler> handlers) {
        var registeredHandlers = new HashMap<String, OutboxMessageHandler>();
        for (OutboxMessageHandler handler : handlers) {
            var previousHandler = registeredHandlers.put(handler.topic(), handler);
            if (previousHandler != null) {
                throw new IllegalStateException("Duplicate outbox handler for topic " + handler.topic());
            }
        }
        this.handlers = Map.copyOf(registeredHandlers);
    }

    public Optional<OutboxMessageHandler> findHandler(String topic) {
        return Optional.ofNullable(handlers.get(topic));
    }
}
