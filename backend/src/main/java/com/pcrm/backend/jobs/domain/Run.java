package com.pcrm.backend.jobs.domain;

import com.pcrm.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Run {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "run_number", nullable = false)
    private Integer runNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RunStatus status;

    @Column(name = "resource_class", length = 60)
    private String resourceClass;

    @Column(name = "requested_timeout_minutes")
    private Long requestedTimeoutMinutes;

    @Column(name = "quota_reservation_id")
    private UUID quotaReservationId;

    @Column(name = "nomad_job_id", length = 180)
    private String nomadJobId;

    @Column(name = "nomad_eval_id", length = 180)
    private String nomadEvalId;

    @Column(name = "nomad_allocation_id", length = 180)
    private String nomadAllocationId;

    @Column(name = "queued_at")
    private OffsetDateTime queuedAt;

    @Column(name = "dispatch_requested_at")
    private OffsetDateTime dispatchRequestedAt;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "process_finished_at")
    private OffsetDateTime processFinishedAt;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "active_lease_expires_at")
    private OffsetDateTime activeLeaseExpiresAt;

    @Column(name = "current_lease_reserved_minutes", nullable = false)
    @Builder.Default
    private Long currentLeaseReservedMinutes = 0L;

    @Column(name = "lease_sequence", nullable = false)
    @Builder.Default
    private Long leaseSequence = 0L;

    @Column(name = "lease_settled", nullable = false)
    @Builder.Default
    private Boolean leaseSettled = false;

    @Column(name = "total_consumed_minutes", nullable = false)
    @Builder.Default
    private Long totalConsumedMinutes = 0L;

    @Column(name = "terminal_reason", length = 120)
    private String terminalReason;

    @Column(name = "created_at", updatable = false, nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    @PrePersist
    protected void ensureNewDefaults() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (id == null) {
            id = UUID.randomUUID();
        }
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
