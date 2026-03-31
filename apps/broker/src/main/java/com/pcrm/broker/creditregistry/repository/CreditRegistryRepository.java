package com.pcrm.broker.creditregistry.repository;

import com.pcrm.broker.creditregistry.domain.CreditRegistryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CreditRegistryRepository extends JpaRepository<CreditRegistryEntry, UUID> {
}
