package com.pcrm.broker.service;

import java.util.Optional;
import java.util.UUID;

import com.pcrm.broker.domain.user.User;
import com.pcrm.broker.domain.user.UserRepository;
import com.pcrm.broker.domain.user.UserRole;
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
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("getUserByUsername returns user when found")
    void getUserByUsername_existingUser_returnsUser() {
        // given
        String username = "testuser";
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email("test@example.com")
                .role(UserRole.STUDENT)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // when
        User result = authService.getUserByUsername(username);

        // then
        assertThat(result.getUsername()).isEqualTo(username);
        assertThat(result.getRole()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    @DisplayName("getUserByUsername throws ResourceNotFoundException when not found")
    void getUserByUsername_notFound_throwsException() {
        // given
        String username = "nonexistent";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> authService.getUserByUsername(username))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining(username);
    }

    @Test
    @DisplayName("getUserById returns user when found")
    void getUserById_existingUser_returnsUser() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        User result = authService.getUserById(userId);

        // then
        assertThat(result.getId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getUserById throws ResourceNotFoundException when not found")
    void getUserById_notFound_throwsException() {
        // given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> authService.getUserById(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User")
                .hasMessageContaining(userId.toString());
    }
}
