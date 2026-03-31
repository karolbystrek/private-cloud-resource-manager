package com.pcrm.broker.wallet.service;

import com.pcrm.broker.user.User;
import com.pcrm.broker.wallet.domain.Wallet;
import com.pcrm.broker.wallet.repository.WalletRepository;
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
