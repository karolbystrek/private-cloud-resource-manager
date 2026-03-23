package com.pcrm.broker.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.pcrm.broker.domain.user.User;
import com.pcrm.broker.domain.wallet.Wallet;
import com.pcrm.broker.domain.wallet.WalletRepository;
import com.pcrm.broker.dto.WalletBalanceResponse;
import com.pcrm.broker.exception.ResourceNotFoundException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("getBalance returns correct balance for existing user")
    void getBalance_existingUser_returnsBalance() {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        BigDecimal balance = new BigDecimal("150.5000");

        User user = User.builder().id(userId).build();
        Wallet wallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .balanceCu(balance)
                .build();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        // when
        WalletBalanceResponse response = walletService.getBalance(userId);

        // then
        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.balanceCu()).isEqualByComparingTo(balance);
    }

    @Test
    @DisplayName("getBalance returns zero balance for user with empty wallet")
    void getBalance_zeroBalance_returnsZero() {
        // given
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        User user = User.builder().id(userId).build();
        Wallet wallet = Wallet.builder()
                .id(walletId)
                .user(user)
                .balanceCu(BigDecimal.ZERO)
                .build();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        // when
        WalletBalanceResponse response = walletService.getBalance(userId);

        // then
        assertThat(response.balanceCu()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getBalance throws ResourceNotFoundException when wallet does not exist")
    void getBalance_noWallet_throwsNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> walletService.getBalance(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Wallet")
                .hasMessageContaining(userId.toString());
    }
}
