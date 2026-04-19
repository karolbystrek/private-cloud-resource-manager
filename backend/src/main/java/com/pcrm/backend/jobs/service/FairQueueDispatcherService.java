package com.pcrm.backend.jobs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.exception.NomadDispatchException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nodes.domain.NodeStatus;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.quota.service.QuotaFairnessSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FairQueueDispatcherService {

    private static final TypeReference<Map<String, String>> ENV_VARS_TYPE = new TypeReference<>() {
    };

    private final JobRepository jobRepository;
    private final NodeRepository nodeRepository;
    private final NomadDispatchClient nomadDispatchClient;
    private final QuotaAccountingService quotaAccountingService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private final Map<UUID, Double> userDeficits = new ConcurrentHashMap<>();

    @Value("${app.scheduler.dispatch.base-quantum:25}")
    private double baseQuantum;

    @Value("${app.scheduler.dispatch.age-boost-per-minute:2}")
    private double ageBoostPerMinute;

    @Scheduled(fixedDelayString = "${app.scheduler.dispatch.interval-ms:3000}")
    public void dispatchNextQueuedJob() {
        var queuedJobs = jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.QUEUED);
        if (queuedJobs.isEmpty()) {
            return;
        }

        var clusterCapacity = loadClusterCapacity();
        if (clusterCapacity.totalCpu() <= 0 || clusterCapacity.totalRamMb() <= 0) {
            return;
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var cycleDeficits = new HashMap<UUID, Double>();
        var candidate = pickBestCandidate(queuedJobs, clusterCapacity, now, cycleDeficits);
        if (candidate.isEmpty()) {
            return;
        }
        cycleDeficits.forEach(userDeficits::put);

        var selected = candidate.get();
        var claimed = transactionTemplate.execute(status ->
                jobRepository.transitionStatus(selected.job().getId(), JobStatus.QUEUED, JobStatus.PENDING)
        );

        if (claimed == null || claimed == 0) {
            return;
        }

        try {
            var jobRequest = toSubmissionRequest(selected.job());
            nomadDispatchClient.dispatchJob(selected.job().getUser().getId(), selected.job().getId(), jobRequest);
            userDeficits.compute(selected.job().getUser().getId(), (_, value) ->
                    (value == null ? 0.0d : value) - selected.cost());
        } catch (NomadDispatchException ex) {
            log.error("Failed to dispatch queued job {}", selected.job().getId(), ex);
            transactionTemplate.executeWithoutResult(status -> compensateDispatchFailure(selected.job().getId()));
        }
    }

    private Optional<CandidateJob> pickBestCandidate(
            java.util.List<Job> queuedJobs,
            ClusterCapacity clusterCapacity,
            OffsetDateTime now,
            Map<UUID, Double> cycleDeficits
    ) {
        return queuedJobs.stream()
                .filter(job -> isRunnable(job, clusterCapacity))
                .map(job -> scoreCandidate(job, clusterCapacity, now, cycleDeficits))
                .max(Comparator
                        .comparingDouble(CandidateJob::score)
                        .thenComparingLong(CandidateJob::queueAgeMinutes));
    }

    private CandidateJob scoreCandidate(
            Job job,
            ClusterCapacity clusterCapacity,
            OffsetDateTime now,
            Map<UUID, Double> cycleDeficits
    ) {
        var userId = job.getUser().getId();
        QuotaFairnessSnapshot fairnessSnapshot = quotaAccountingService.loadFairnessSnapshot(userId, now);

        double usageRatio = fairnessSnapshot.usageRatio();
        double quantum = baseQuantum * fairnessSnapshot.roleWeight() * (1.0d + (1.0d - usageRatio));
        double updatedDeficit = cycleDeficits.computeIfAbsent(
                userId,
                ignored -> userDeficits.getOrDefault(userId, 0.0d) + quantum
        );

        double cost = calculateDominantCost(job, clusterCapacity);
        OffsetDateTime queuedAt = job.getQueuedAt() != null ? job.getQueuedAt() : job.getCreatedAt();
        long queueMinutes = queuedAt == null ? 0L : Math.max(0L, Duration.between(queuedAt, now).toMinutes());
        double ageBoost = queueMinutes * ageBoostPerMinute;
        double score = updatedDeficit - cost + ageBoost;

        return new CandidateJob(job, score, cost, queueMinutes);
    }

    private boolean isRunnable(Job job, ClusterCapacity clusterCapacity) {
        return job.getReqCpuCores() <= clusterCapacity.totalCpu()
                && (job.getReqRamGb() * 1024L) <= clusterCapacity.totalRamMb();
    }

    private double calculateDominantCost(Job job, ClusterCapacity clusterCapacity) {
        double cpuShare = job.getReqCpuCores() / (double) clusterCapacity.totalCpu();
        double ramShare = (job.getReqRamGb() * 1024.0d) / clusterCapacity.totalRamMb();
        return Math.max(cpuShare, ramShare) * 100.0d;
    }

    private ClusterCapacity loadClusterCapacity() {
        long totalCpu = 0L;
        long totalRamMb = 0L;

        var nodes = nodeRepository.findAll();
        for (var node : nodes) {
            if (node.getStatus() != NodeStatus.AVAILABLE) {
                continue;
            }
            if (Boolean.TRUE.equals(node.getDraining())) {
                continue;
            }
            if ("ineligible".equalsIgnoreCase(node.getSchedulingEligibility())) {
                continue;
            }

            totalCpu += node.getTotalCpuCores();
            totalRamMb += node.getTotalRamMb();
        }

        return new ClusterCapacity(totalCpu, totalRamMb);
    }

    private JobSubmissionRequest toSubmissionRequest(Job job) {
        try {
            var envVars = objectMapper.readValue(job.getEnvVarsJson(), ENV_VARS_TYPE);
            return new JobSubmissionRequest(
                    job.getDockerImage(),
                    job.getExecutionCommand(),
                    job.getReqCpuCores(),
                    job.getReqRamGb(),
                    envVars
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize job environment variables", ex);
        }
    }

    private void compensateDispatchFailure(UUID jobId) {
        var job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getLeaseSettled()) {
            return;
        }

        quotaAccountingService.refundLeaseReservation(
                job,
                job.getCurrentLeaseReservedMinutes(),
                "Nomad dispatch failed, initial lease refunded"
        );

        job.setCurrentLeaseReservedMinutes(0L);
        job.setLeaseSettled(true);
        job.setStatus(JobStatus.FAILED);
        job.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        jobRepository.save(job);
    }

    private record ClusterCapacity(long totalCpu, long totalRamMb) {
    }

    private record CandidateJob(Job job, double score, double cost, long queueAgeMinutes) {
    }
}
