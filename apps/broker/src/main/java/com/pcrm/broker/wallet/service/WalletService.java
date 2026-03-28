package com.pcrm.broker.wallet.service;

import com.pcrm.broker.exception.ResourceNotFoundException;
import com.pcrm.broker.wallet.Wallet;
import com.pcrm.broker.wallet.dto.WalletBalanceResponse;
import com.pcrm.broker.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletService {

    private final WalletRepository walletRepository;

    /**
     * Retrieve the CU balance for the given user.
     *
     * @param userId UUID of the user
     * @return wallet balance details
     * @throws ResourceNotFoundException if no wallet exists for the user
     */
    public WalletBalanceResponse getBalance(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "userId", userId.toString()));

        return new WalletBalanceResponse(
                wallet.getId(),
                userId,
                wallet.getBalanceCu()
        );
    }
}
