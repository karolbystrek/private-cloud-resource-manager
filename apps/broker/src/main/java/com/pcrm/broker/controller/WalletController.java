package com.pcrm.broker.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pcrm.broker.dto.WalletBalanceResponse;
import com.pcrm.broker.service.WalletService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * GET /api/wallets/{userId} — retrieve the CU balance for a user.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<WalletBalanceResponse> getBalance(@PathVariable UUID userId) {
        return ResponseEntity.ok(walletService.getBalance(userId));
    }
}
