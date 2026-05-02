package com.pcrm.backend.events.repository;

import com.pcrm.backend.events.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    Optional<OutboxMessage> findByEventIdAndTopic(UUID eventId, String topic);
}
