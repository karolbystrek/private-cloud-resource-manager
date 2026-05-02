package com.pcrm.backend.events.repository;

import com.pcrm.backend.events.domain.DomainEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DomainEventRepository extends JpaRepository<DomainEvent, UUID> {
}
