package com.pcrm.backend.events.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "aggregate_sequences")
@IdClass(AggregateSequenceId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregateSequence {

    @Id
    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Id
    @Column(name = "aggregate_id", nullable = false, length = 255)
    private String aggregateId;

    @Column(name = "next_sequence_number", nullable = false)
    @Builder.Default
    private Long nextSequenceNumber = 1L;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
