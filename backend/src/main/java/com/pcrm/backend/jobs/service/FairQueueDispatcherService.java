package com.pcrm.backend.jobs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.service.EventConsumerDedupeService;
import com.pcrm.backend.events.service.EventTopics;
import com.pcrm.backend.events.service.OutboxMessageHandler;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nodes.domain.NodeStatus;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.nomad.NomadDispatchRequest;
import com.pcrm.backend.nomad.NomadDispatchResult;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.quota.service.QuotaFairnessSnapshot;
import com.pcrm.backend.storage.service.JobArtifactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.nomad.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class FairQueueDispatcherService implements OutboxMessageHandler {

    private static final TypeReference<Map<String, String>> ENV_VARS_TYPE = new TypeReference<>() {
    };
    private static final String CONSUMER_NAME = "fair-queue-dispatcher";
    private static final int MAX_ERROR_LENGTH = 4000;

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final NodeRepository nodeRepository;
    private final NomadDispatchClient nomadDispatchClient;
    private final QuotaAccountingService quotaAccountingService;
    private final JobArtifactService jobArtifactService;
    private final EventConsumerDedupeService dedupeService;
    private final JobEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    private final Map<UUID, Double> userDeficits = new ConcurrentHashMap<>();

    @Value("${app.scheduler.dispatch.base-quantum:25}")
    private double baseQuantum;

    @Value("${app.scheduler.dispatch.age-boost-per-minute:2}")
    private double ageBoostPerMinute;

    @Value("${app.scheduler.dispatch.retry-stale-after-ms:60000}")
    private long retryStaleAfterMs;

    @Override
    public String topic() {
        return EventTopics.JOB_QUEUED;
    }

    @Override
    public void handle(OutboxMessage message) {
        dedupeService.runOnce(CONSUMER_NAME, message.getEventId(), () ->
                requestNextDispatch(extractCorrelationId(message))
        );
    }

    @Scheduled(fixedDelayString = "${app.scheduler.dispatch.interval-ms:3000}")
    public void requestNextDispatchFromQueue() {
        requestNextDispatch(UUID.randomUUID());
    }

    private void requestNextDispatch(UUID correlationId) {
        if (retryStaleDispatchingJob(correlationId)) {
            return;
        }

        var selected = selectCandidate();
        if (selected.isEmpty()) {
            return;
        }

        var candidate = selected.get();
        var claimedJob = transactionTemplate.execute(_ -> claimForDispatch(candidate.job().getId()));

        if (claimedJob == null || claimedJob.isEmpty()) {
            return;
        }

        var job = claimedJob.get();
        var dispatched = dispatchClaimedJob(job, correlationId);
        if (!dispatched) {
            return;
        }

        userDeficits.compute(candidate.job().getProfile().getId(), (_, value) ->
                (value == null ? 0.0d : value) - candidate.cost());
    }

    private boolean retryStaleDispatchingJob(UUID correlationId) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var staleBefore = now.minus(Duration.ofMillis(retryStaleAfterMs));
        var staleJob = jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.DISPATCHING).stream()
                .filter(job -> job.getDispatchedAt() == null)
                .filter(job -> job.getDispatchRequestedAt() != null && job.getDispatchRequestedAt().isBefore(staleBefore))
                .filter(job -> hasDispatchableReservation(job, now))
                .findFirst();

        if (staleJob.isEmpty()) {
            return false;
        }

        var retryJob = transactionTemplate.execute(_ -> prepareDispatchRetry(staleJob.get().getId()));
        return retryJob != null && retryJob.isPresent() && dispatchClaimedJob(retryJob.get(), correlationId);
    }

    private boolean dispatchClaimedJob(Job job, UUID correlationId) {
        NomadDispatchResult dispatchResult;
        try {
            dispatchResult = nomadDispatchClient.dispatchJob(toDispatchRequest(job, correlationId));
        } catch (Exception ex) {
            log.error("Failed to dispatch queued job {}", job.getId(), ex);
            transactionTemplate.executeWithoutResult(_ ->
                    compensateDispatchFailure(job.getId(), summarize(ex), correlationId)
            );
            return false;
        }

        transactionTemplate.executeWithoutResult(_ ->
                markDispatchAccepted(job.getId(), dispatchResult, correlationId)
        );
        return true;
    }

    private Optional<CandidateJob> selectCandidate() {
        var queuedJobs = jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.QUEUED);
        if (queuedJobs.isEmpty()) {
            return Optional.empty();
        }

        var clusterCapacity = loadClusterCapacity();
        if (clusterCapacity.totalCpu() <= 0 || clusterCapacity.totalRamMb() <= 0) {
            return Optional.empty();
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var cycleDeficits = new HashMap<UUID, Double>();
        var candidate = pickBestCandidate(queuedJobs, clusterCapacity, now, cycleDeficits);
        if (candidate.isPresent()) {
            userDeficits.putAll(cycleDeficits);
        }
        return candidate;
    }

    private Optional<CandidateJob> pickBestCandidate(
            java.util.List<Job> queuedJobs,
            ClusterCapacity clusterCapacity,
            OffsetDateTime now,
            Map<UUID, Double> cycleDeficits
    ) {
        return queuedJobs.stream()
                .filter(job -> hasDispatchableReservation(job, now))
                .filter(job -> isRunnable(job, clusterCapacity))
                .map(job -> scoreCandidate(job, clusterCapacity, now, cycleDeficits))
                .max(Comparator
                        .comparingDouble(CandidateJob::score)
                        .thenComparingLong(CandidateJob::queueAgeMinutes));
    }

    private boolean hasDispatchableReservation(Job job, OffsetDateTime now) {
        return job.getCurrentLeaseReservedMinutes() > 0
                && job.getActiveLeaseExpiresAt() != null
                && job.getActiveLeaseExpiresAt().isAfter(now);
    }

    private CandidateJob scoreCandidate(
            Job job,
            ClusterCapacity clusterCapacity,
            OffsetDateTime now,
            Map<UUID, Double> cycleDeficits
    ) {
        var profileId = job.getProfile().getId();
        QuotaFairnessSnapshot fairnessSnapshot = quotaAccountingService.loadFairnessSnapshot(profileId, now);

        double usageRatio = fairnessSnapshot.usageRatio();
        double quantum = baseQuantum * fairnessSnapshot.roleWeight() * (1.0d + (1.0d - usageRatio));
        double updatedDeficit = cycleDeficits.computeIfAbsent(
                profileId,
                ignored -> userDeficits.getOrDefault(profileId, 0.0d) + quantum
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

    private Optional<Job> claimForDispatch(UUID jobId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() != JobStatus.QUEUED) {
            return Optional.empty();
        }

        jobArtifactService.ensurePendingArtifact(job);
        var savedJob = jobStateMachine.markDispatchRequested(job, OffsetDateTime.now(ZoneOffset.UTC));
        log.debug("Requested dispatch for job {} as Nomad job {}", job.getId(), nomadJobId(job));
        return Optional.of(savedJob);
    }

    private Optional<Job> prepareDispatchRetry(UUID jobId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() != JobStatus.DISPATCHING || job.getDispatchedAt() != null) {
            return Optional.empty();
        }

        return Optional.of(jobStateMachine.markDispatchRetryRequested(job, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private NomadDispatchRequest toDispatchRequest(Job job, UUID correlationId) {
        return new NomadDispatchRequest(
                job.getProfile().getId(),
                job.getId(),
                nomadJobId(job),
                job.getDockerImage(),
                job.getExecutionCommand(),
                job.getReqCpuCores(),
                job.getReqRamGb(),
                deserializeEnvVars(job),
                correlationId
        );
    }

    private Map<String, String> deserializeEnvVars(Job job) {
        try {
            return objectMapper.readValue(job.getEnvVarsJson(), ENV_VARS_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize job environment variables", ex);
        }
    }

    private void markDispatchAccepted(UUID jobId, NomadDispatchResult dispatchResult, UUID correlationId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() != JobStatus.DISPATCHING) {
            return;
        }

        jobStateMachine.markDispatchAccepted(job, OffsetDateTime.now(ZoneOffset.UTC));
        eventPublisher.jobEvent(
                "JobDispatched",
                job,
                Map.of(
                        "nomadJobId", dispatchResult.nomadJobId() == null ? "" : dispatchResult.nomadJobId(),
                        "nomadEvalId", dispatchResult.nomadEvalId() == null ? "" : dispatchResult.nomadEvalId()
                ),
                "backend",
                correlationId
        );
    }

    private String nomadJobId(Job job) {
        return job.getId().toString();
    }

    private void compensateDispatchFailure(UUID jobId, String reason, UUID correlationId) {
        var job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.DISPATCHING) {
            return;
        }

        if (!Boolean.TRUE.equals(job.getLeaseSettled())) {
            quotaAccountingService.refundLeaseReservation(
                    job,
                    job.getCurrentLeaseReservedMinutes(),
                    "Nomad dispatch failed, initial lease refunded"
            );

            jobStateMachine.markCurrentLeaseSettled(job, 0L);
        }

        jobStateMachine.markDispatchFailed(job, OffsetDateTime.now(ZoneOffset.UTC));
        eventPublisher.jobEvent(
                "JobInfraFailed",
                job,
                Map.of("reason", reason == null ? "" : reason),
                "backend",
                correlationId
        );
    }

    private String summarize(Exception ex) {
        var message = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
        if (message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }

    private UUID extractCorrelationId(OutboxMessage message) {
        var rawCorrelationId = message.getHeaders().path("correlation_id").asText(null);
        if (rawCorrelationId == null || rawCorrelationId.isBlank()) {
            return UUID.randomUUID();
        }
        return UUID.fromString(rawCorrelationId);
    }

    private record ClusterCapacity(long totalCpu, long totalRamMb) {
    }

    private record CandidateJob(Job job, double score, double cost, long queueAgeMinutes) {
    }
}
