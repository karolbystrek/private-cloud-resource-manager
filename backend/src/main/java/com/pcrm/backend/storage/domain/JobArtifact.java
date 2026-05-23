package com.pcrm.backend.storage.domain;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.user.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "job_artifacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobArtifact {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private JobArtifactStatus status;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "created_at", updatable = false, nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @PrePersist
    protected void ensureDefaults() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
