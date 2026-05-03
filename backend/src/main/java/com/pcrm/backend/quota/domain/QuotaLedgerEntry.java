package com.pcrm.backend.quota.domain;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.user.Profile;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "quota_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotaLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private Run run;

    @Column(name = "lease_seq", nullable = false)
    @Builder.Default
    private Long leaseSequence = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private QuotaLedgerEntryType entryType;

    @Column(nullable = false)
    private Long minutes;

    @Column
    private String reason;

    @Column(name = "created_at", updatable = false, nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
