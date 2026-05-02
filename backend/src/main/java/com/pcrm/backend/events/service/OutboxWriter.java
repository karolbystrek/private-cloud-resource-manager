package com.pcrm.backend.events.service;

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
                        .availableAt(now)
                        .createdAt(now)
                        .attemptCount(0)
                        .build())
                .toList();

        if (!messages.isEmpty()) {
            outboxMessageRepository.saveAll(messages);
        }
    }
}
