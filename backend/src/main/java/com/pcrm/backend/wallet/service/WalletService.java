package com.pcrm.backend.wallet.service;

import com.pcrm.backend.user.User;
import com.pcrm.backend.wallet.domain.Wallet;
import com.pcrm.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    public void createWallet(User user) {
        var wallet = Wallet.builder()
                .balance(0L)
                .user(user)
                .build();
        walletRepository.save(wallet);
    }
}
