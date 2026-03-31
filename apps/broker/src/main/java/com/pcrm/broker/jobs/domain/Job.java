package com.pcrm.broker.jobs.domain;

import com.pcrm.broker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "node_id")
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(name = "docker_image", nullable = false)
    private String dockerImage;

    @Column(name = "execution_command", nullable = false)
    private String executionCommand;

    @Column(name = "req_cpu_cores", nullable = false)
    private Integer reqCpuCores;

    @Column(name = "req_ram_gb", nullable = false)
    private Integer reqRamGb;

    @Column(name = "req_gpu_count", nullable = false)
    @Builder.Default
    private Integer reqGpuCount = 0;

    @Column(name = "total_cost_credits", nullable = false)
    @Builder.Default
    private Long totalCostCredits = 0L;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
