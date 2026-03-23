package com.pcrm.broker.controller;

import java.math.BigDecimal;
import java.util.UUID;

import com.pcrm.broker.dto.WalletBalanceResponse;
import com.pcrm.broker.exception.GlobalExceptionHandler;
import com.pcrm.broker.exception.ResourceNotFoundException;
import com.pcrm.broker.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test for WalletController (no Spring context needed).
 */
@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WalletController walletController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(walletController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/wallets/{userId} returns 200 with balance")
    void getBalance_existingUser_returns200() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        BigDecimal balance = new BigDecimal("250.7500");

        when(walletService.getBalance(userId))
                .thenReturn(new WalletBalanceResponse(walletId, userId, balance));

        // when / then
        mockMvc.perform(get("/api/wallets/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.balanceCu").value(250.75));
    }

    @Test
    @DisplayName("GET /api/wallets/{userId} returns 404 when wallet not found")
    void getBalance_noWallet_returns404() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        when(walletService.getBalance(userId))
                .thenThrow(new ResourceNotFoundException("Wallet", "userId", userId.toString()));

        // when / then
        mockMvc.perform(get("/api/wallets/{userId}", userId))
                .andExpect(status().isNotFound());
    }
}
