package com.pcrm.broker.auth.service;

import com.pcrm.broker.auth.domain.CustomUserDetails;
import com.pcrm.broker.auth.domain.RefreshToken;
import com.pcrm.broker.auth.dto.AuthenticationRequest;
import com.pcrm.broker.auth.dto.TokenPair;
import com.pcrm.broker.auth.repository.RefreshTokenRepository;
import com.pcrm.broker.exception.TokenRefreshException;
import com.pcrm.broker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.pcrm.broker.exception.TokenRefreshException.tokenRefreshException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public TokenPair authenticate(AuthenticationRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()
                )
        );
        var userDetails = (CustomUserDetails) authentication.getPrincipal();
        return issueTokenPair(userDetails);
    }

    @Transactional
    public TokenPair refresh(String token) {
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

        return issueTokenPair(userDetails);
    }

    @Transactional
    public void logout(String token) {
        var jti = jwtService.extractTokenId(token);
        refreshTokenRepository.revokeTokenIfActive(jti);
    }

    private TokenPair issueTokenPair(CustomUserDetails userDetails) {
        var accessToken = jwtService.generateToken(userDetails);
        var jti = UUID.randomUUID().toString();
        var refreshTokenStr = jwtService.generateRefreshToken(userDetails, jti);

        var refreshToken = RefreshToken.builder()
                .user(userDetails.user())
                .tokenId(jti)
                .expiresAt(OffsetDateTime.now().plusSeconds(jwtService.getRefreshExpiration() / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, refreshTokenStr);
    }
}
