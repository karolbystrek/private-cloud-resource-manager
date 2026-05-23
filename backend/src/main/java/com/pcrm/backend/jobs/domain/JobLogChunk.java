package com.pcrm.backend.jobs.domain;

import com.pcrm.backend.user.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "job_log_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobLogChunk {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id", nullable = false)
    private JobLogStream logStream;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false, length = 20)
    private String stream;

    @Column(nullable = false)
    private Long sequence;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "offset_start", nullable = false)
    private Long offsetStart;

    @Column(name = "offset_end", nullable = false)
    private Long offsetEnd;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "created_at", updatable = false, nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);

    @PrePersist
    protected void ensureDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
