package com.pcrm.backend.events.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pcrm.backend.events.domain.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OutboxClaimRepository {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public List<OutboxMessage> claimAvailable(
            int batchSize,
            OffsetDateTime now,
            Duration claimTimeout,
            String claimedBy
    ) {
        var staleClaimBefore = now.minus(claimTimeout);
        return jdbcTemplate.query(
                """
                        WITH candidate AS (
                            SELECT id
                            FROM outbox
                            WHERE published_at IS NULL
                              AND available_at <= ?
                              AND (claimed_at IS NULL OR claimed_at < ?)
                            ORDER BY available_at ASC, created_at ASC
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                        )
                        UPDATE outbox outbox_message
                        SET claimed_at = ?,
                            claimed_by = ?,
                            attempt_count = outbox_message.attempt_count + 1
                        FROM candidate
                        WHERE outbox_message.id = candidate.id
                        RETURNING outbox_message.id,
                                  outbox_message.event_id,
                                  outbox_message.topic,
                                  outbox_message.payload::text AS payload,
                                  outbox_message.available_at,
                                  outbox_message.published_at,
                                  outbox_message.claimed_at,
                                  outbox_message.claimed_by,
                                  outbox_message.attempt_count,
                                  outbox_message.last_error,
                                  outbox_message.created_at
                        """,
                (preparedStatement) -> {
                    preparedStatement.setObject(1, now);
                    preparedStatement.setObject(2, staleClaimBefore);
                    preparedStatement.setInt(3, batchSize);
                    preparedStatement.setObject(4, now);
                    preparedStatement.setString(5, claimedBy);
                },
                (resultSet, _) -> mapOutboxMessage(resultSet)
        );
    }

    public int markPublished(UUID messageId, OffsetDateTime publishedAt) {
        return jdbcTemplate.update(
                """
                        UPDATE outbox
                        SET published_at = ?,
                            claimed_at = NULL,
                            claimed_by = NULL
                        WHERE id = ?
                          AND published_at IS NULL
                        """,
                publishedAt,
                messageId
        );
    }

    public int markFailed(UUID messageId, OffsetDateTime availableAt, String lastError) {
        return jdbcTemplate.update(
                """
                        UPDATE outbox
                        SET available_at = ?,
                            claimed_at = NULL,
                            claimed_by = NULL,
                            last_error = ?
                        WHERE id = ?
                          AND published_at IS NULL
                        """,
                availableAt,
                lastError,
                messageId
        );
    }

    private OutboxMessage mapOutboxMessage(ResultSet resultSet) throws SQLException {
        return OutboxMessage.builder()
                .id(resultSet.getObject("id", UUID.class))
                .eventId(resultSet.getObject("event_id", UUID.class))
                .topic(resultSet.getString("topic"))
                .payload(readPayload(resultSet.getString("payload")))
                .availableAt(resultSet.getObject("available_at", OffsetDateTime.class))
                .publishedAt(resultSet.getObject("published_at", OffsetDateTime.class))
                .claimedAt(resultSet.getObject("claimed_at", OffsetDateTime.class))
                .claimedBy(resultSet.getString("claimed_by"))
                .attemptCount(resultSet.getInt("attempt_count"))
                .lastError(resultSet.getString("last_error"))
                .createdAt(resultSet.getObject("created_at", OffsetDateTime.class))
                .build();
    }

    private JsonNode readPayload(String payload) throws SQLException {
        try {
            return jsonMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            throw new SQLException("Failed to deserialize outbox payload", ex);
        }
    }
}
