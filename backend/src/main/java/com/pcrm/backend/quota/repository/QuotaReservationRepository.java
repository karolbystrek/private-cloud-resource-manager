package com.pcrm.backend.quota.repository;

import com.pcrm.backend.quota.domain.QuotaReservation;
import com.pcrm.backend.quota.domain.QuotaReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotaReservationRepository extends JpaRepository<QuotaReservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reservation
            FROM QuotaReservation reservation
            WHERE reservation.job.id = :jobId
              AND reservation.status = :status
            """)
    Optional<QuotaReservation> findByJobIdAndStatusForUpdate(
            @Param("jobId") UUID jobId,
            @Param("status") QuotaReservationStatus status
    );
}
