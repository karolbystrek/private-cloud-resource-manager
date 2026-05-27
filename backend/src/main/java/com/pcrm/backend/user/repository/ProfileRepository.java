package com.pcrm.backend.user.repository;

import com.pcrm.backend.user.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    interface AdminUserDirectoryRow {
        UUID getId();

        String getEmail();

        String getRole();
    }

    @Query(value = "SELECT email FROM auth.users WHERE id = :id", nativeQuery = true)
    Optional<String> findEmailForAuthUser(@Param("id") UUID id);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM auth.users WHERE id = :id)", nativeQuery = true)
    boolean existsAuthUserById(@Param("id") UUID id);

    @Query(value = """
            SELECT p.id as id, u.email as email, p.role as role
            FROM profiles p
            JOIN auth.users u ON u.id = p.id
            WHERE p.role = 'STUDENT'
            ORDER BY lower(u.email) ASC
            """, nativeQuery = true)
    List<AdminUserDirectoryRow> findAllForAdminQuotaSelection();
}
