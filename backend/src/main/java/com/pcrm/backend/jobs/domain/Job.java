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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "node_id")
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

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

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @PrePersist
    protected void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
