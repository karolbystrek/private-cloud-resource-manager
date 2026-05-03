package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.QuotaGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QuotaGrantRepository extends JpaRepository<QuotaGrant, UUID> {
}
