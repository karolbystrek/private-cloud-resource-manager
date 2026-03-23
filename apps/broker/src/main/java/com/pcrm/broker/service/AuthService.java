package com.pcrm.broker.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pcrm.broker.domain.user.User;
import com.pcrm.broker.domain.user.UserRepository;
import com.pcrm.broker.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Mock authentication service.
 * <p>
 * Provides basic user lookup by username or ID.
 * Will be replaced with JWT-based authentication in a future milestone.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;

    /**
     * Find a user by username or throw 404.
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    /**
     * Find a user by ID or throw 404.
     */
    public User getUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
