package com.pcrm.backend.jobs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.service.OutboxConsumerDedupeService;
import com.pcrm.backend.events.service.OutboxMessageHandler;
import com.pcrm.backend.events.service.OutboxTopics;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nodes.domain.NodeStatus;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.nomad.NomadDispatchRequest;
import com.pcrm.backend.nomad.NomadDispatchResult;
import com.pcrm.backend.quota.service.QuotaAccountingService;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.nomad.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class FifoJobDispatcherService implements OutboxMessageHandler {

    private static final TypeReference<Map<String, String>> ENV_VARS_TYPE = new TypeReference<>() {
    };
    private static final String CONSUMER_NAME = "fifo-job-dispatcher";
    private static final int MAX_ERROR_LENGTH = 4000;

    private final JobRepository jobRepository;
    private final JobStateMachine jobStateMachine;
    private final NodeRepository nodeRepository;
    private final NomadDispatchClient nomadDispatchClient;
    private final QuotaAccountingService quotaAccountingService;
    private final JobArtifactService jobArtifactService;
    private final OutboxConsumerDedupeService dedupeService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.scheduler.dispatch.retry-stale-after-ms:60000}")
    private long retryStaleAfterMs;

    @Override
    public String topic() {
        return OutboxTopics.JOB_QUEUED;
    }

    @Override
    public void handle(OutboxMessage message) {
        dedupeService.runOnce(CONSUMER_NAME, message.getId(), () ->
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

        var clusterCapacity = loadClusterCapacity();
        if (clusterCapacity.totalCpu() <= 0 || clusterCapacity.totalRamMb() <= 0) {
            return;
        }

        var claimedJob = transactionTemplate.execute(_ -> claimNextDispatchableJob(clusterCapacity));
        if (claimedJob == null || claimedJob.isEmpty()) {
            return;
        }

        dispatchClaimedJob(claimedJob.get(), correlationId);
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
                    compensateDispatchFailure(job.getId(), summarize(ex))
            );
            return false;
        }

        transactionTemplate.executeWithoutResult(_ ->
                markDispatchAccepted(job.getId(), dispatchResult)
        );
        return true;
    }

    private Optional<Job> claimNextDispatchableJob(ClusterCapacity clusterCapacity) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var jobId = jobRepository.findNextQueuedDispatchCandidateIdForUpdate(
                now,
                clusterCapacity.totalCpu(),
                clusterCapacity.totalRamMb()
        );
        if (jobId.isEmpty()) {
            return Optional.empty();
        }

        var job = jobRepository.findByIdForUpdate(jobId.get()).orElseThrow();
        if (job.getStatus() != JobStatus.QUEUED || !hasDispatchableReservation(job, now) || !isRunnable(job, clusterCapacity)) {
            return Optional.empty();
        }

        jobArtifactService.ensurePendingArtifact(job);
        var savedJob = jobStateMachine.markDispatchRequested(job, now);
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

    private boolean hasDispatchableReservation(Job job, OffsetDateTime now) {
        return job.getCurrentLeaseReservedMinutes() > 0
                && job.getActiveLeaseExpiresAt() != null
                && job.getActiveLeaseExpiresAt().isAfter(now);
    }

    private boolean isRunnable(Job job, ClusterCapacity clusterCapacity) {
        return job.getReqCpuCores() <= clusterCapacity.totalCpu()
                && (job.getReqRamGb() * 1024L) <= clusterCapacity.totalRamMb();
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

    private void markDispatchAccepted(UUID jobId, NomadDispatchResult dispatchResult) {
        var job = jobRepository.findByIdForUpdate(jobId).orElseThrow();
        if (job.getStatus() != JobStatus.DISPATCHING) {
            return;
        }

        jobStateMachine.markDispatchAccepted(job, OffsetDateTime.now(ZoneOffset.UTC));
        log.info("Dispatched job {} to Nomad job {} eval {}", job.getId(), dispatchResult.nomadJobId(), dispatchResult.nomadEvalId());
    }

    private String nomadJobId(Job job) {
        return job.getId().toString();
    }

    private void compensateDispatchFailure(UUID jobId, String reason) {
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
        log.warn("Marked job {} as INFRA_FAILED after dispatch failure: {}", job.getId(), reason);
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
}
