package com.pcrm.backend.events.repository;

import com.pcrm.backend.events.domain.EventConsumerDedupe;
import com.pcrm.backend.events.domain.EventConsumerDedupeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventConsumerDedupeRepository extends JpaRepository<EventConsumerDedupe, EventConsumerDedupeId> {
}
