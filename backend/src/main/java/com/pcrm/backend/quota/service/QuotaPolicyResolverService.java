package com.pcrm.backend.quota.service;

import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.quota.domain.QuotaPolicy;
import com.pcrm.backend.quota.domain.UserQuotaOverride;
import com.pcrm.backend.quota.dto.QuotaPolicyResponse;
import com.pcrm.backend.quota.dto.admin.UpsertQuotaOverrideRequest;
import com.pcrm.backend.quota.dto.admin.UpsertQuotaPolicyRequest;
import com.pcrm.backend.quota.repository.QuotaPolicyRepository;
import com.pcrm.backend.quota.repository.UserQuotaOverrideRepository;
import com.pcrm.backend.user.User;
import com.pcrm.backend.user.UserRole;
import com.pcrm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaPolicyResolverService {

    private final QuotaPolicyRepository quotaPolicyRepository;
    private final UserQuotaOverrideRepository userQuotaOverrideRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public EffectiveQuotaPolicy resolveEffectivePolicy(User user, OffsetDateTime at) {
        var activeOverride = userQuotaOverrideRepository.findActiveOverride(user.getId(), at);
        if (activeOverride.isPresent()) {
            var override = activeOverride.get();
            return new EffectiveQuotaPolicy(
                    user.getRole(),
                    override.getMonthlyMinutes(),
                    override.getRoleWeight(),
                    override.getUnlimited(),
                    true
            );
        }

        var rolePolicy = findRolePolicy(user.getRole(), at);
        return new EffectiveQuotaPolicy(
                user.getRole(),
                rolePolicy.getMonthlyMinutes(),
                rolePolicy.getRoleWeight(),
                rolePolicy.getUnlimited(),
                false
        );
    }

    @Transactional(readOnly = true)
    public QuotaPolicy findRolePolicy(UserRole role, OffsetDateTime at) {
        return quotaPolicyRepository.findFirstByRoleAndActiveFromLessThanEqualOrderByActiveFromDesc(role, at)
                .orElseThrow(() -> new ResourceNotFoundException("QuotaPolicy", "role", role.name()));
    }

    @Transactional
    public QuotaPolicyResponse upsertRolePolicy(UserRole role, UpsertQuotaPolicyRequest request) {
        var policy = QuotaPolicy.builder()
                .role(role)
                .monthlyMinutes(request.monthlyMinutes())
                .roleWeight(request.roleWeight())
                .unlimited(request.unlimited())
                .activeFrom(defaultActiveFrom(request.activeFrom()))
                .build();

        var saved = quotaPolicyRepository.save(policy);
        return new QuotaPolicyResponse(
                saved.getId(),
                null,
                saved.getRole(),
                saved.getMonthlyMinutes(),
                saved.getRoleWeight(),
                saved.getUnlimited(),
                saved.getActiveFrom(),
                null
        );
    }

    @Transactional
    public QuotaPolicyResponse upsertUserOverride(UUID userId, UpsertQuotaOverrideRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        var override = UserQuotaOverride.builder()
                .user(user)
                .monthlyMinutes(request.monthlyMinutes())
                .roleWeight(request.roleWeight())
                .unlimited(request.unlimited())
                .activeFrom(defaultActiveFrom(request.activeFrom()))
                .expiresAt(request.expiresAt())
                .build();

        var saved = userQuotaOverrideRepository.save(override);
        return new QuotaPolicyResponse(
                saved.getId(),
                user.getId(),
                user.getRole(),
                saved.getMonthlyMinutes(),
                saved.getRoleWeight(),
                saved.getUnlimited(),
                saved.getActiveFrom(),
                saved.getExpiresAt()
        );
    }

    private OffsetDateTime defaultActiveFrom(OffsetDateTime activeFrom) {
        if (activeFrom != null) {
            return activeFrom;
        }
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
