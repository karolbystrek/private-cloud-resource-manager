package com.pcrm.backend.jobs.domain;

import com.pcrm.backend.user.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "job_log_streams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobLogStream {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false, length = 20)
    private String stream;

    @Column(name = "last_offset", nullable = false)
    @Builder.Default
    private Long lastOffset = 0L;

    @Column(name = "capture_complete", nullable = false)
    @Builder.Default
    private Boolean captureComplete = false;

    @Column(name = "last_error")
    private String lastError;

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
        if (lastOffset == null) {
            lastOffset = 0L;
        }
        if (captureComplete == null) {
            captureComplete = false;
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
