package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.ResourceWeightPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceWeightPolicyRepository extends JpaRepository<ResourceWeightPolicy, Short> {
}
