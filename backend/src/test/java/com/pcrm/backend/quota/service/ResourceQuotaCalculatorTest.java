package com.pcrm.backend.quota.service;

import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.quota.domain.ResourceWeightPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceQuotaCalculatorTest {

    private final ResourceQuotaCalculator calculator = new ResourceQuotaCalculator();

    @Test
    void calculatesCpuAndRamUnits() {
        var job = Job.builder()
                .reqCpuCores(2)
                .reqRamGb(9)
                .gpuEnabled(false)
                .gpuCount(0)
                .build();

        var units = calculator.calculate(job, policy());

        assertThat(units.cpuUnits()).isEqualTo(2);
        assertThat(units.ramUnits()).isEqualTo(3);
        assertThat(units.gpuUnits()).isZero();
        assertThat(units.totalUnits()).isEqualTo(5);
    }

    @Test
    void appliesDefaultGpuWeightWhenMinimumMemoryIsNotRequested() {
        var job = Job.builder()
                .reqCpuCores(1)
                .reqRamGb(4)
                .gpuEnabled(true)
                .gpuCount(2)
                .gpuMinMemoryGb(null)
                .build();

        var units = calculator.calculate(job, policy());

        assertThat(units.gpuUnits()).isEqualTo(32);
        assertThat(units.totalUnits()).isEqualTo(34);
    }

    @Test
    void appliesLargestMatchingGpuMemoryTier() {
        var job = Job.builder()
                .reqCpuCores(1)
                .reqRamGb(4)
                .gpuEnabled(true)
                .gpuCount(1)
                .gpuMinMemoryGb(24)
                .build();

        var units = calculator.calculate(job, policy());

        assertThat(units.gpuUnits()).isEqualTo(32);
        assertThat(units.totalUnits()).isEqualTo(34);
    }

    @Test
    void appliesCustomCpuRamAndGpuWeights() {
        var job = Job.builder()
                .reqCpuCores(2)
                .reqRamGb(5)
                .gpuEnabled(true)
                .gpuCount(1)
                .gpuMinMemoryGb(40)
                .build();
        var policy = ResourceWeightPolicy.builder()
                .cpuCoreWeight(3)
                .ramGbPerUnit(2)
                .ramUnitWeight(4)
                .gpuWeightTiers(List.of(
                        new ResourceWeightPolicy.GpuWeightTier(0, 10),
                        new ResourceWeightPolicy.GpuWeightTier(40, 50)
                ))
                .build();

        var units = calculator.calculate(job, policy);

        assertThat(units.cpuUnits()).isEqualTo(6);
        assertThat(units.ramUnits()).isEqualTo(12);
        assertThat(units.gpuUnits()).isEqualTo(50);
        assertThat(calculator.calculateComputeMinutes(15, units.totalUnits())).isEqualTo(1020);
    }

    private ResourceWeightPolicy policy() {
        return ResourceWeightPolicy.builder()
                .cpuCoreWeight(1)
                .ramGbPerUnit(4)
                .ramUnitWeight(1)
                .gpuWeightTiers(List.of(
                        new ResourceWeightPolicy.GpuWeightTier(0, 16),
                        new ResourceWeightPolicy.GpuWeightTier(16, 24),
                        new ResourceWeightPolicy.GpuWeightTier(24, 32),
                        new ResourceWeightPolicy.GpuWeightTier(40, 48)
                ))
                .build();
    }
}
