package com.pcrm.backend.events.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventConsumerDedupeService {

    public static final String LOCAL_SOURCE = "backend";

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryStartProcessing(String consumerName, UUID eventId) {
        return tryStartProcessing(consumerName, LOCAL_SOURCE, eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryStartProcessing(String consumerName, String source, UUID eventId) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName is required");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }

        var rows = jdbcTemplate.update(
                """
                        INSERT INTO event_consumer_dedupe (consumer_name, source, event_id, processed_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (consumer_name, source, event_id) DO NOTHING
                        """,
                consumerName,
                source,
                eventId,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        return rows == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean runOnce(String consumerName, UUID eventId, Runnable action) {
        return runOnce(consumerName, LOCAL_SOURCE, eventId, action);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean runOnce(String consumerName, String source, UUID eventId, Runnable action) {
        if (!tryStartProcessing(consumerName, source, eventId)) {
            return false;
        }
        action.run();
        return true;
    }
}
