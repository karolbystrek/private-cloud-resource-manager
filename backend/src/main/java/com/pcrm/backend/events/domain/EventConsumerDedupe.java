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
import java.util.UUID;

@Entity
@Table(name = "event_consumer_dedupe")
@IdClass(EventConsumerDedupeId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventConsumerDedupe {

    @Id
    @Column(name = "consumer_name", nullable = false, length = 120)
    private String consumerName;

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private OffsetDateTime processedAt = OffsetDateTime.now(ZoneOffset.UTC);
}
