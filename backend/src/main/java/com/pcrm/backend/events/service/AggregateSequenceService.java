package com.pcrm.backend.events.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AggregateSequenceService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public long allocateNext(String aggregateType, String aggregateId) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        Long sequenceNumber = jdbcTemplate.queryForObject(
                """
                        INSERT INTO aggregate_sequences (aggregate_type, aggregate_id, next_sequence_number, updated_at)
                        VALUES (?, ?, 2, ?)
                        ON CONFLICT (aggregate_type, aggregate_id)
                        DO UPDATE SET next_sequence_number = aggregate_sequences.next_sequence_number + 1,
                                      updated_at = EXCLUDED.updated_at
                        RETURNING next_sequence_number - 1
                        """,
                Long.class,
                aggregateType,
                aggregateId,
                now
        );

        if (sequenceNumber == null) {
            throw new IllegalStateException("Failed to allocate aggregate event sequence");
        }
        return sequenceNumber;
    }
}
