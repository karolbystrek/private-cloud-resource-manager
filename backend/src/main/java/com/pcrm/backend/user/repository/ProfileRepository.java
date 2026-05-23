package com.pcrm.backend.user.repository;

import com.pcrm.backend.user.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    @Query(value = "SELECT email FROM auth.users WHERE id = :id", nativeQuery = true)
    Optional<String> findEmailForAuthUser(@Param("id") UUID id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM auth.users WHERE id = :id)", nativeQuery = true)
    boolean existsAuthUserById(@Param("id") UUID id);
}
