package com.pcrm.backend.auth.service;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.auth.dto.AuthenticationResult;
import com.pcrm.backend.auth.dto.AuthenticationRequest;
import com.pcrm.backend.auth.dto.RegistrationRequest;
import com.pcrm.backend.auth.dto.TokenPair;
import com.pcrm.backend.auth.repository.RefreshTokenRepository;
import com.pcrm.backend.exception.RegistrationConflictException;
import com.pcrm.backend.exception.TokenRefreshException;
import com.pcrm.backend.user.User;
import com.pcrm.backend.user.UserRole;
import com.pcrm.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.pcrm.backend.exception.TokenRefreshException.tokenRefreshException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthenticationResult authenticate(AuthenticationRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );
        var userDetails = (CustomUserDetails) authentication.getPrincipal();
        return issueAuthenticationResult(userDetails);
    }

    @Transactional
    public AuthenticationResult refresh(String token) {
        var username = jwtService.extractUsername(token);
        var jti = jwtService.extractTokenId(token);

        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        var userDetails = new CustomUserDetails(user);

        if (!jwtService.isTokenValid(token, userDetails)) {
            throw tokenRefreshException();
        }

        var refreshSession = refreshTokenRepository.findByTokenId(jti)
                .orElseThrow(TokenRefreshException::tokenRefreshException);

        if (refreshSession.isRevoked() || refreshSession.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw tokenRefreshException();
        }

        int updatedRows = refreshTokenRepository.revokeTokenIfActive(jti);
        if (updatedRows == 0) {
            // Token was already revoked by another concurrent request. This is reuse attempt!
            throw tokenRefreshException();
        }

        return issueAuthenticationResult(userDetails);
    }

    @Transactional
    public void logout(String token) {
        var jti = jwtService.extractTokenId(token);
        refreshTokenRepository.revokeTokenIfActive(jti);
    }

    @Transactional
    public void register(RegistrationRequest request, boolean isAdminRegistration) {
        if (userRepository.existsByUsername(request.username())) {
            throw new RegistrationConflictException("Username already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new RegistrationConflictException("Email already exists");
        }

        var userRole = UserRole.STUDENT;
        if (isAdminRegistration && request.role() != null) {
            userRole = request.role();
        }

        var user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(userRole)
                .build();

        userRepository.save(user);
    }

    private AuthenticationResult issueAuthenticationResult(CustomUserDetails userDetails) {
        TokenPair tokenPair = jwtService.issueTokenPair(userDetails);
        return new AuthenticationResult(tokenPair, userDetails.user().getRole());
    }
}
