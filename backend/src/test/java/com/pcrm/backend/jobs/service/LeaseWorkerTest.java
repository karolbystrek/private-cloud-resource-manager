package com.pcrm.backend.jobs.service;

import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nomad.NomadJobControlClient;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.user.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class LeaseWorkerTest {

    private JobRepository jobRepository;
    private JobStateMachine jobStateMachine;
    private QuotaAccountingService quotaAccountingService;
    private NomadJobControlClient nomadJobControlClient;
    private JobEventPublisher eventPublisher;
    private LeaseWorker leaseWorker;

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        jobStateMachine = new JobStateMachine(jobRepository);
        quotaAccountingService = mock(QuotaAccountingService.class);
        nomadJobControlClient = mock(NomadJobControlClient.class);
        eventPublisher = mock(JobEventPublisher.class);
        leaseWorker = new LeaseWorker(
                jobRepository,
                jobStateMachine,
                quotaAccountingService,
                nomadJobControlClient,
                eventPublisher,
                immediateTransactionTemplate()
        );
        ReflectionTestUtils.setField(leaseWorker, "safetyWindowMs", 120_000L);
        ReflectionTestUtils.setField(leaseWorker, "batchSize", 50);
    }

    @Test
    void renewsLeaseBeforeExpiryWhenQuotaIsAvailable() {
        var originalExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30);
        var job = job(JobStatus.RUNNING, originalExpiry);
        job.setStartedAt(originalExpiry.minusMinutes(10));
        job.setCurrentLeaseReservedMinutes(15L);
        job.setLeaseSequence(1L);

        when(jobRepository.findLeaseEnforcementCandidatesForUpdate(anyCollection(), any(), any()))
                .thenReturn(List.of(job));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(quotaAccountingService.getLeaseMinutes()).thenReturn(15L);
        when(quotaAccountingService.reserveAdditionalLease(
                eq(job.getProfile().getId()),
                same(job),
                eq(originalExpiry.plusMinutes(15)),
                anyString()
        )).thenReturn(15L);

        leaseWorker.enforceDueLeases("test");

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getActiveLeaseExpiresAt()).isEqualTo(originalExpiry.plusMinutes(15));
        assertThat(job.getCurrentLeaseReservedMinutes()).isEqualTo(30L);
        assertThat(job.getLeaseSequence()).isEqualTo(2L);
        assertThat(job.getLastLeaseRenewalError()).isNull();
        verify(nomadJobControlClient, never()).stopJob(anyString());
    }

    @Test
    void stopsNomadAndTimesOutJobWhenRenewalQuotaIsUnavailable() {
        var originalExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30);
        var job = job(JobStatus.RUNNING, originalExpiry);
        job.setStartedAt(originalExpiry.minusMinutes(15));
        job.setCurrentLeaseReservedMinutes(15L);
        job.setLeaseSequence(1L);

        when(jobRepository.findLeaseEnforcementCandidatesForUpdate(anyCollection(), any(), any()))
                .thenReturn(List.of(job));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(quotaAccountingService.getLeaseMinutes()).thenReturn(15L);
        when(quotaAccountingService.reserveAdditionalLease(any(), same(job), any(), anyString()))
                .thenThrow(new InsufficientQuotaException(0, 15));

        leaseWorker.enforceDueLeases("test");

        assertThat(job.getStatus()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(job.getTerminalReason()).startsWith("InsufficientQuotaException");
        assertThat(job.getLeaseSettled()).isTrue();
        assertThat(job.getActiveLeaseExpiresAt()).isNull();
        assertThat(job.getCurrentLeaseReservedMinutes()).isZero();
        verify(nomadJobControlClient).stopJob(job.getId().toString());
        verify(quotaAccountingService).settleLeaseMinutes(same(job), eq(15L), eq(15L), anyString());
    }

    @Test
    void stopsNomadAndTimesOutJobWhenLeaseAlreadyExpired() {
        var expiredAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(5);
        var job = job(JobStatus.RUNNING, expiredAt);
        job.setStartedAt(expiredAt.minusMinutes(15));

        when(jobRepository.findLeaseEnforcementCandidatesForUpdate(anyCollection(), any(), any()))
                .thenReturn(List.of(job));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        leaseWorker.enforceDueLeases("test");

        assertThat(job.getStatus()).isEqualTo(JobStatus.TIMED_OUT);
        assertThat(job.getTerminalReason()).isEqualTo("LEASE_EXPIRED");
        assertThat(job.getLeaseSettled()).isTrue();
        verify(quotaAccountingService, never()).reserveAdditionalLease(any(), any(), any(), anyString());
        verify(nomadJobControlClient).stopJob(job.getId().toString());
        verify(quotaAccountingService).settleLeaseMinutes(same(job), eq(15L), eq(15L), anyString());
    }

    private Job job(JobStatus status, OffsetDateTime activeLeaseExpiresAt) {
        var profile = Profile.builder()
                .id(UUID.randomUUID())
                .build();
        return Job.builder()
                .id(UUID.randomUUID())
                .profile(profile)
                .status(status)
                .activeLeaseExpiresAt(activeLeaseExpiresAt)
                .currentLeaseReservedMinutes(15L)
                .leaseSequence(1L)
                .leaseSettled(false)
                .leaseRenewalAttemptCount(0L)
                .totalConsumedMinutes(0L)
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
