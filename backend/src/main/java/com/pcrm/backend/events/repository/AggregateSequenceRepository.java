package com.pcrm.backend.events.repository;

import com.pcrm.backend.events.domain.AggregateSequence;
import com.pcrm.backend.events.domain.AggregateSequenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AggregateSequenceRepository extends JpaRepository<AggregateSequence, AggregateSequenceId> {
}
