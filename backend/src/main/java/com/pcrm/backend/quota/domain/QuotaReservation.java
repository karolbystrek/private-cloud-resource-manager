package com.pcrm.backend.quota.domain;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.user.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "quota_reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotaReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "interval_start", nullable = false)
    private OffsetDateTime intervalStart;

    @Column(name = "interval_end", nullable = false)
    private OffsetDateTime intervalEnd;

    @Column(name = "reserved_compute_minutes", nullable = false)
    private Long reservedComputeMinutes;

    @Column(name = "consumed_compute_minutes", nullable = false)
    @Builder.Default
    private Long consumedComputeMinutes = 0L;

    @Column(name = "released_compute_minutes", nullable = false)
    @Builder.Default
    private Long releasedComputeMinutes = 0L;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QuotaReservationStatus status;

    @Column(name = "created_at", updatable = false, nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    @PrePersist
    protected void ensureDefaults() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void updateTimestamp() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
