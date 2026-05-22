package com.pcrm.backend.jobs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.service.EventConsumerDedupeService;
import com.pcrm.backend.events.service.EventTopics;
import com.pcrm.backend.events.service.OutboxMessageHandler;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.repository.RunRepository;
import com.pcrm.backend.nodes.domain.NodeStatus;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.nomad.NomadDispatchRequest;
import com.pcrm.backend.nomad.NomadDispatchResult;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.quota.service.QuotaFairnessSnapshot;
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

    private final RunRepository runRepository;
    private final RunStateMachine runStateMachine;
    private final NodeRepository nodeRepository;
    private final NomadDispatchClient nomadDispatchClient;
    private final QuotaAccountingService quotaAccountingService;
    private final EventConsumerDedupeService dedupeService;
    private final JobRunEventPublisher eventPublisher;
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
        return EventTopics.RUN_QUEUED;
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
        if (retryStaleDispatchingRun(correlationId)) {
            return;
        }

        var selected = selectCandidate();
        if (selected.isEmpty()) {
            return;
        }

        var candidate = selected.get();
        var claimedRun = transactionTemplate.execute(_ -> claimForDispatch(candidate.run().getId()));

        if (claimedRun == null || claimedRun.isEmpty()) {
            return;
        }

        var run = claimedRun.get();
        var dispatched = dispatchClaimedRun(run, correlationId);
        if (!dispatched) {
            return;
        }

        userDeficits.compute(candidate.job().getProfile().getId(), (_, value) ->
                (value == null ? 0.0d : value) - candidate.cost());
    }

    private boolean retryStaleDispatchingRun(UUID correlationId) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var staleBefore = now.minus(Duration.ofMillis(retryStaleAfterMs));
        var staleRun = runRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(RunStatus.DISPATCHING).stream()
                .filter(run -> run.getDispatchedAt() == null)
                .filter(run -> run.getDispatchRequestedAt() != null && run.getDispatchRequestedAt().isBefore(staleBefore))
                .filter(run -> hasDispatchableReservation(run, now))
                .findFirst();

        if (staleRun.isEmpty()) {
            return false;
        }

        var retryRun = transactionTemplate.execute(_ -> prepareDispatchRetry(staleRun.get().getId()));
        return retryRun != null && retryRun.isPresent() && dispatchClaimedRun(retryRun.get(), correlationId);
    }

    private boolean dispatchClaimedRun(Run run, UUID correlationId) {
        NomadDispatchResult dispatchResult;
        try {
            dispatchResult = nomadDispatchClient.dispatchJob(toDispatchRequest(run, correlationId));
        } catch (Exception ex) {
            log.error("Failed to dispatch queued run {}", run.getId(), ex);
            transactionTemplate.executeWithoutResult(_ ->
                    compensateDispatchFailure(run.getId(), summarize(ex), correlationId)
            );
            return false;
        }

        transactionTemplate.executeWithoutResult(_ ->
                markDispatchAccepted(run.getId(), dispatchResult, correlationId)
        );
        return true;
    }

    private Optional<CandidateJob> selectCandidate() {
        var queuedRuns = runRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(RunStatus.QUEUED);
        if (queuedRuns.isEmpty()) {
            return Optional.empty();
        }

        var clusterCapacity = loadClusterCapacity();
        if (clusterCapacity.totalCpu() <= 0 || clusterCapacity.totalRamMb() <= 0) {
            return Optional.empty();
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var cycleDeficits = new HashMap<UUID, Double>();
        var candidate = pickBestCandidate(queuedRuns, clusterCapacity, now, cycleDeficits);
        if (candidate.isPresent()) {
            userDeficits.putAll(cycleDeficits);
        }
        return candidate;
    }

    private Optional<CandidateJob> pickBestCandidate(
            java.util.List<Run> queuedRuns,
            ClusterCapacity clusterCapacity,
            OffsetDateTime now,
            Map<UUID, Double> cycleDeficits
    ) {
        return queuedRuns.stream()
                .filter(run -> hasDispatchableReservation(run, now))
                .filter(run -> isRunnable(run.getJob(), clusterCapacity))
                .map(run -> scoreCandidate(run, clusterCapacity, now, cycleDeficits))
                .max(Comparator
                        .comparingDouble(CandidateJob::score)
                        .thenComparingLong(CandidateJob::queueAgeMinutes));
    }

    private boolean hasDispatchableReservation(Run run, OffsetDateTime now) {
        return run.getQuotaReservationId() != null
                && run.getCurrentLeaseReservedMinutes() > 0
                && run.getActiveLeaseExpiresAt() != null
                && run.getActiveLeaseExpiresAt().isAfter(now);
    }

    private CandidateJob scoreCandidate(
            Run run,
            ClusterCapacity clusterCapacity,
            OffsetDateTime now,
            Map<UUID, Double> cycleDeficits
    ) {
        var job = run.getJob();
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

        return new CandidateJob(run, score, cost, queueMinutes);
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

    private Optional<Run> claimForDispatch(UUID runId) {
        var run = runRepository.findByIdForUpdate(runId).orElseThrow();
        if (run.getStatus() != RunStatus.QUEUED) {
            return Optional.empty();
        }

        var savedRun = runStateMachine.markDispatchRequested(run, OffsetDateTime.now(ZoneOffset.UTC));
        log.debug("Requested dispatch for run {} as Nomad job {}", run.getId(), run.getNomadJobId());
        return Optional.of(savedRun);
    }

    private Optional<Run> prepareDispatchRetry(UUID runId) {
        var run = runRepository.findByIdForUpdate(runId).orElseThrow();
        if (run.getStatus() != RunStatus.DISPATCHING || run.getDispatchedAt() != null) {
            return Optional.empty();
        }

        return Optional.of(runStateMachine.markDispatchRetryRequested(run, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private NomadDispatchRequest toDispatchRequest(Run run, UUID correlationId) {
        var job = run.getJob();
        return new NomadDispatchRequest(
                run.getProfile().getId(),
                job.getId(),
                run.getId(),
                run.getQuotaReservationId(),
                run.getResourceClass(),
                run.getNomadJobId(),
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

    private void markDispatchAccepted(UUID runId, NomadDispatchResult dispatchResult, UUID correlationId) {
        var run = runRepository.findByIdForUpdate(runId).orElseThrow();
        if (run.getStatus() != RunStatus.DISPATCHING) {
            return;
        }

        runStateMachine.markDispatchAccepted(run, dispatchResult, OffsetDateTime.now(ZoneOffset.UTC));
        eventPublisher.runEvent(
                "RunDispatched",
                run,
                Map.of(
                        "nomadJobId", dispatchResult.nomadJobId() == null ? "" : dispatchResult.nomadJobId(),
                        "nomadEvalId", dispatchResult.nomadEvalId() == null ? "" : dispatchResult.nomadEvalId()
                ),
                "backend",
                correlationId
        );
    }

    private void compensateDispatchFailure(UUID runId, String reason, UUID correlationId) {
        var run = runRepository.findByIdForUpdate(runId).orElse(null);
        if (run == null || run.getStatus() != RunStatus.DISPATCHING) {
            return;
        }

        if (!Boolean.TRUE.equals(run.getLeaseSettled())) {
            quotaAccountingService.refundLeaseReservation(
                    run,
                    run.getCurrentLeaseReservedMinutes(),
                    "Nomad dispatch failed, initial lease refunded"
            );

            runStateMachine.markCurrentLeaseSettled(run, 0L);
        }

        runStateMachine.markDispatchFailed(run, reason, OffsetDateTime.now(ZoneOffset.UTC));
        eventPublisher.runEvent(
                "RunInfraFailed",
                run,
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

    private record CandidateJob(Run run, double score, double cost, long queueAgeMinutes) {
        private Job job() {
            return run.getJob();
        }
    }
}
