package com.pcrm.backend.jobs.domain;

import com.pcrm.backend.user.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JobStatus status;

    @Column(name = "docker_image", nullable = false)
    private String dockerImage;

    @Column(name = "execution_command", nullable = false)
    private String executionCommand;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "submission_fingerprint", length = 64)
    private String submissionFingerprint;

    @Column(name = "req_cpu_cores", nullable = false)
    private Integer reqCpuCores;

    @Column(name = "req_ram_gb", nullable = false)
    private Integer reqRamGb;

    @Column(name = "total_consumed_minutes", nullable = false)
    @Builder.Default
    private Long totalConsumedMinutes = 0L;

    @Column(name = "env_vars_json", nullable = false)
    @Builder.Default
    private String envVarsJson = "{}";

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

    @Column(name = "lease_renewal_attempt_count", nullable = false)
    @Builder.Default
    private Long leaseRenewalAttemptCount = 0L;

    @Column(name = "last_lease_renewal_error")
    private String lastLeaseRenewalError;

    @Column(name = "lease_stop_requested_at")
    private OffsetDateTime leaseStopRequestedAt;

    @Column(name = "terminal_reason", length = 120)
    private String terminalReason;

    @Column(name = "created_at", updatable = false, nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    @PrePersist
    protected void ensureDefaults() {
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
