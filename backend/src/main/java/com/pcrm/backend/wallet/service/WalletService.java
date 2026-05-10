package com.pcrm.backend.wallet.service;

import com.pcrm.backend.user.Profile;
import com.pcrm.backend.wallet.domain.Wallet;
import com.pcrm.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    public void createWalletForProfile(Profile profile) {
        var wallet = Wallet.builder()
                .profile(profile)
                .balance(0L)
                .build();
        walletRepository.save(wallet);
    }
}
