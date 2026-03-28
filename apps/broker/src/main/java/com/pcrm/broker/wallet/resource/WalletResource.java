package com.pcrm.broker.wallet.resource;

import com.pcrm.broker.wallet.dto.WalletBalanceResponse;
import com.pcrm.broker.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletResource {

    private final WalletService walletService;

    /**
     * GET /api/wallets/{userId} — retrieve the CU balance for a user.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<WalletBalanceResponse> getBalance(@PathVariable UUID userId) {
        return ResponseEntity.ok(walletService.getBalance(userId));
    }
}
