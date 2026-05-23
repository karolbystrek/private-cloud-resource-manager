package com.pcrm.backend.auth.service;

import com.pcrm.backend.user.Profile;
import com.pcrm.backend.user.UserRole;
import com.pcrm.backend.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileProvisioningService {

    private final ProfileRepository profileRepository;

    @Transactional
    public Profile ensureProfile(UUID authUserId) {
        return profileRepository.findById(authUserId).orElseGet(() -> {
            if (!profileRepository.existsAuthUserById(authUserId)) {
                throw new BadCredentialsException("Authenticated user no longer exists.");
            }

            return profileRepository.save(Profile.builder()
                    .id(authUserId)
                    .role(UserRole.STUDENT)
                    .build());
        });
    }
}
