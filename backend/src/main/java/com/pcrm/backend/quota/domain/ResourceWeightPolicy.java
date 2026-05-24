package com.pcrm.backend.quota.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Entity
@Table(name = "resource_weight_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceWeightPolicy {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "cpu_core_weight", nullable = false)
    private Integer cpuCoreWeight;

    @Column(name = "ram_gb_per_unit", nullable = false)
    private Integer ramGbPerUnit;

    @Column(name = "ram_unit_weight", nullable = false)
    private Integer ramUnitWeight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gpu_weight_tiers", nullable = false)
    private List<GpuWeightTier> gpuWeightTiers;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);

    public record GpuWeightTier(
            int minMemoryGb,
            int weight
    ) {
    }
}
