package com.pcrm.backend.jobs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcrm.backend.events.service.OutboxConsumerDedupeService;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nodes.domain.Node;
import com.pcrm.backend.nodes.domain.NodeStatus;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.nomad.NomadDispatchRequest;
import com.pcrm.backend.nomad.NomadDispatchResult;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.storage.service.JobArtifactService;
import com.pcrm.backend.user.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FifoJobDispatcherServiceTest {

    private JobRepository jobRepository;
    private NodeRepository nodeRepository;
    private NomadDispatchClient nomadDispatchClient;
    private QuotaAccountingService quotaAccountingService;
    private JobArtifactService jobArtifactService;
    private FifoJobDispatcherService dispatcherService;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var jobStateMachine = new JobStateMachine(jobRepository);
        nodeRepository = mock(NodeRepository.class);
        nomadDispatchClient = mock(NomadDispatchClient.class);
        quotaAccountingService = mock(QuotaAccountingService.class);
        jobArtifactService = mock(JobArtifactService.class);
        dispatcherService = new FifoJobDispatcherService(
                jobRepository,
                jobStateMachine,
                nodeRepository,
                nomadDispatchClient,
                quotaAccountingService,
                jobArtifactService,
                mock(OutboxConsumerDedupeService.class),
                immediateTransactionTemplate(),
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(dispatcherService, "retryStaleAfterMs", 60_000L);
    }

    @Test
    void claimsOldestDispatchableQueuedJobAndSendsItToNomad() {
        var job = queuedJob(2, 4);
        when(jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.DISPATCHING))
                .thenReturn(List.of());
        when(nodeRepository.findAll()).thenReturn(List.of(node(8, 16_384)));
        when(jobRepository.findNextQueuedDispatchCandidateIdForUpdate(any(), eq(8L), eq(16_384L)))
                .thenReturn(Optional.of(job.getId()));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(nomadDispatchClient.dispatchJob(any())).thenReturn(new NomadDispatchResult(job.getId().toString(), "eval-1"));

        dispatcherService.requestNextDispatchFromQueue();

        assertThat(job.getStatus()).isEqualTo(JobStatus.SCHEDULING);
        assertThat(job.getDispatchRequestedAt()).isNotNull();
        assertThat(job.getDispatchedAt()).isNotNull();
        verify(jobArtifactService).ensurePendingArtifact(job);

        var request = ArgumentCaptor.forClass(NomadDispatchRequest.class);
        verify(nomadDispatchClient).dispatchJob(request.capture());
        assertThat(request.getValue().nomadJobId()).isEqualTo(job.getId().toString());
        assertThat(request.getValue().reqCpuCores()).isEqualTo(2);
        assertThat(request.getValue().reqRamGb()).isEqualTo(4);
    }

    @Test
    void doesNotClaimQueuedJobsWhenCachedCapacityIsEmpty() {
        when(jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.DISPATCHING))
                .thenReturn(List.of());
        when(nodeRepository.findAll()).thenReturn(List.of());

        dispatcherService.requestNextDispatchFromQueue();

        verify(jobRepository, never()).findNextQueuedDispatchCandidateIdForUpdate(any(), anyLong(), anyLong());
        verify(nomadDispatchClient, never()).dispatchJob(any());
    }

    @Test
    void dispatchesRepositorySelectedFittingJob() {
        var fittingJob = queuedJob(2, 4);
        when(jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.DISPATCHING))
                .thenReturn(List.of());
        when(nodeRepository.findAll()).thenReturn(List.of(node(4, 8_192)));
        when(jobRepository.findNextQueuedDispatchCandidateIdForUpdate(any(), eq(4L), eq(8_192L)))
                .thenReturn(Optional.of(fittingJob.getId()));
        when(jobRepository.findByIdForUpdate(fittingJob.getId())).thenReturn(Optional.of(fittingJob));
        when(nomadDispatchClient.dispatchJob(any()))
                .thenReturn(new NomadDispatchResult(fittingJob.getId().toString(), "eval-2"));

        dispatcherService.requestNextDispatchFromQueue();

        assertThat(fittingJob.getStatus()).isEqualTo(JobStatus.SCHEDULING);
        verify(nomadDispatchClient).dispatchJob(any());
    }

    @Test
    void dispatchFailureRefundsLeaseAndMarksInfraFailed() {
        var job = queuedJob(2, 4);
        when(jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.DISPATCHING))
                .thenReturn(List.of());
        when(nodeRepository.findAll()).thenReturn(List.of(node(8, 16_384)));
        when(jobRepository.findNextQueuedDispatchCandidateIdForUpdate(any(), eq(8L), eq(16_384L)))
                .thenReturn(Optional.of(job.getId()));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(nomadDispatchClient.dispatchJob(any())).thenThrow(new IllegalStateException("nomad unavailable"));

        dispatcherService.requestNextDispatchFromQueue();

        assertThat(job.getStatus()).isEqualTo(JobStatus.INFRA_FAILED);
        assertThat(job.getLeaseSettled()).isTrue();
        assertThat(job.getCurrentLeaseReservedMinutes()).isZero();
        assertThat(job.getActiveLeaseExpiresAt()).isNull();
        verify(quotaAccountingService).refundLeaseReservation(
                same(job),
                eq(15L),
                eq("Nomad dispatch failed, initial lease refunded")
        );
    }

    @Test
    void retriesStaleDispatchingJobBeforeClaimingNewQueuedJob() {
        var staleJob = queuedJob(2, 4);
        staleJob.setStatus(JobStatus.DISPATCHING);
        staleJob.setDispatchRequestedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        when(jobRepository.findTop100ByStatusOrderByQueuedAtAscCreatedAtAsc(JobStatus.DISPATCHING))
                .thenReturn(List.of(staleJob));
        when(jobRepository.findByIdForUpdate(staleJob.getId())).thenReturn(Optional.of(staleJob));
        when(nomadDispatchClient.dispatchJob(any()))
                .thenReturn(new NomadDispatchResult(staleJob.getId().toString(), "eval-3"));

        dispatcherService.requestNextDispatchFromQueue();

        assertThat(staleJob.getStatus()).isEqualTo(JobStatus.SCHEDULING);
        verify(nodeRepository, never()).findAll();
        verify(jobRepository, never()).findNextQueuedDispatchCandidateIdForUpdate(any(), anyLong(), anyLong());
        verify(nomadDispatchClient).dispatchJob(any());
    }

    private Job queuedJob(int cpuCores, int ramGb) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        return Job.builder()
                .id(UUID.randomUUID())
                .profile(Profile.builder().id(UUID.randomUUID()).build())
                .status(JobStatus.QUEUED)
                .dockerImage("ubuntu:24.04")
                .executionCommand("echo ok")
                .reqCpuCores(cpuCores)
                .reqRamGb(ramGb)
                .envVarsJson("{}")
                .queuedAt(now.minusMinutes(5))
                .activeLeaseExpiresAt(now.plusMinutes(10))
                .currentLeaseReservedMinutes(15L)
                .leaseSequence(1L)
                .leaseSettled(false)
                .totalConsumedMinutes(0L)
                .createdAt(now.minusMinutes(10))
                .updatedAt(now.minusMinutes(10))
                .build();
    }

    private Node node(int cpuCores, int ramMb) {
        return Node.builder()
                .id(UUID.randomUUID().toString())
                .hostname("node-1")
                .ipAddress("127.0.0.1")
                .status(NodeStatus.AVAILABLE)
                .schedulingEligibility("eligible")
                .draining(false)
                .totalCpuCores(cpuCores)
                .totalRamMb(ramMb)
                .agentVersion("test")
                .build();
    }

    private TransactionTemplate immediateTransactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
