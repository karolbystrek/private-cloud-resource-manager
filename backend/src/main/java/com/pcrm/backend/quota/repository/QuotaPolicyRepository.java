package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.QuotaPolicy;
import com.pcrm.backend.user.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotaPolicyRepository extends JpaRepository<QuotaPolicy, UUID> {

    Optional<QuotaPolicy> findFirstByRoleAndActiveFromLessThanEqualOrderByActiveFromDesc(UserRole role, OffsetDateTime activeFrom);

    List<QuotaPolicy> findByRoleOrderByActiveFromDesc(UserRole role);
}
