package com.pcrm.broker.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for wallet balance queries.
 */
public record WalletBalanceResponse(
        UUID walletId,
        UUID userId,
        BigDecimal balanceCu
) {
}
