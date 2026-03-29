package com.pcrm.broker.auth.service;

import com.pcrm.broker.auth.domain.CustomUserDetails;
import com.pcrm.broker.auth.domain.RefreshToken;
import com.pcrm.broker.auth.dto.TokenPair;
import com.pcrm.broker.auth.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final JwtKeyProvider jwtKeyProvider;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-expiration}")
    @Getter
    private long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public TokenPair issueTokenPair(CustomUserDetails userDetails) {
        var accessToken = generateToken(userDetails);
        var jti = UUID.randomUUID().toString();
        var refreshTokenStr = generateRefreshToken(userDetails, jti);

        var refreshToken = RefreshToken.builder()
                .user(userDetails.user())
                .tokenId(jti)
                .expiresAt(OffsetDateTime.now().plusSeconds(getRefreshExpiration() / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, refreshTokenStr);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private String generateToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(jwtKeyProvider.getPrivateKey(), Jwts.SIG.EdDSA)
                .compact();
    }

    private String generateRefreshToken(UserDetails userDetails, String jti) {
        return Jwts.builder()
                .id(jti)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(jwtKeyProvider.getPrivateKey(), Jwts.SIG.EdDSA)
                .compact();
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractTokenId(String token) {
        return extractClaim(token, Claims::getId);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtKeyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
