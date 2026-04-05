package com.pcrm.backend.auth.repository;

import com.pcrm.backend.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Modifying
    @Query("UPDATE RefreshToken token SET token.revoked = true WHERE token.tokenId = :tokenId AND token.revoked = false")
    int revokeTokenIfActive(@Param("tokenId") String tokenId);

    Optional<RefreshToken> findByTokenId(String tokenId);
}
