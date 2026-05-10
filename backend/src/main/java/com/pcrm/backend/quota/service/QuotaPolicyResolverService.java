package com.pcrm.backend.quota.service;

import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.quota.domain.QuotaPolicy;
import com.pcrm.backend.quota.domain.UserQuotaOverride;
import com.pcrm.backend.quota.dto.QuotaPolicyResponse;
import com.pcrm.backend.quota.dto.admin.UpsertQuotaOverrideRequest;
import com.pcrm.backend.quota.dto.admin.UpsertQuotaPolicyRequest;
import com.pcrm.backend.quota.repository.QuotaPolicyRepository;
import com.pcrm.backend.quota.repository.UserQuotaOverrideRepository;
import com.pcrm.backend.user.Profile;
import com.pcrm.backend.user.UserRole;
import com.pcrm.backend.user.repository.ProfileRepository;
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
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public EffectiveQuotaPolicy resolveEffectivePolicy(Profile profile, OffsetDateTime at) {
        var activeOverride = userQuotaOverrideRepository.findActiveOverride(profile.getId(), at);
        if (activeOverride.isPresent()) {
            var override = activeOverride.get();
            return new EffectiveQuotaPolicy(
                    profile.getRole(),
                    override.getMonthlyMinutes(),
                    override.getRoleWeight(),
                    override.getUnlimited(),
                    true
            );
        }

        var rolePolicy = findRolePolicy(profile.getRole(), at);
        return new EffectiveQuotaPolicy(
                profile.getRole(),
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
        var profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId));

        var override = UserQuotaOverride.builder()
                .profile(profile)
                .monthlyMinutes(request.monthlyMinutes())
                .roleWeight(request.roleWeight())
                .unlimited(request.unlimited())
                .activeFrom(defaultActiveFrom(request.activeFrom()))
                .expiresAt(request.expiresAt())
                .build();

        var saved = userQuotaOverrideRepository.save(override);
        return new QuotaPolicyResponse(
                saved.getId(),
                profile.getId(),
                profile.getRole(),
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
