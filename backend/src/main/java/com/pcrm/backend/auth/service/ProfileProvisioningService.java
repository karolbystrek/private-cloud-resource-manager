package com.pcrm.backend.auth.service;

import com.pcrm.backend.user.Profile;
import com.pcrm.backend.user.UserRole;
import com.pcrm.backend.user.repository.ProfileRepository;
import com.pcrm.backend.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileProvisioningService {

    private final ProfileRepository profileRepository;
    private final WalletService walletService;

    @Transactional
    public Profile ensureProfileAndWallet(UUID authUserId) {
        return profileRepository.findById(authUserId).orElseGet(() -> {
            var profile = Profile.builder()
                    .id(authUserId)
                    .role(UserRole.STUDENT)
                    .build();
            Profile saved = profileRepository.save(profile);
            walletService.createWalletForProfile(saved);
            return saved;
        });
    }
}
