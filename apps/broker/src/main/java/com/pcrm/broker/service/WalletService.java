package com.pcrm.broker.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pcrm.broker.domain.wallet.Wallet;
import com.pcrm.broker.domain.wallet.WalletRepository;
import com.pcrm.broker.dto.WalletBalanceResponse;
import com.pcrm.broker.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

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
