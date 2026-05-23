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
public class OutboxConsumerDedupeService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryStartProcessing(String consumerName, UUID messageId) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName is required");
        }
        if (messageId == null) {
            throw new IllegalArgumentException("messageId is required");
        }

        var rows = jdbcTemplate.update(
                """
                        INSERT INTO outbox_consumer_dedupe (consumer_name, message_id, processed_at)
                        VALUES (?, ?, ?)
                        ON CONFLICT (consumer_name, message_id) DO NOTHING
                        """,
                consumerName,
                messageId,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        return rows == 1;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean runOnce(String consumerName, UUID messageId, Runnable action) {
        if (!tryStartProcessing(consumerName, messageId)) {
            return false;
        }
        action.run();
        return true;
    }
}
