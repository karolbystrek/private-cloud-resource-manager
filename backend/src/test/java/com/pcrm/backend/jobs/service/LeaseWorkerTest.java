package com.pcrm.backend.jobs.service;

import com.pcrm.backend.exception.InsufficientQuotaException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.Run;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.repository.RunRepository;
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

    private RunRepository runRepository;
    private JobRepository jobRepository;
    private QuotaAccountingService quotaAccountingService;
    private NomadJobControlClient nomadJobControlClient;
    private JobRunEventPublisher eventPublisher;
    private LeaseWorker leaseWorker;

    @BeforeEach
    void setUp() {
        runRepository = mock(RunRepository.class);
        jobRepository = mock(JobRepository.class);
        quotaAccountingService = mock(QuotaAccountingService.class);
        nomadJobControlClient = mock(NomadJobControlClient.class);
        eventPublisher = mock(JobRunEventPublisher.class);
        leaseWorker = new LeaseWorker(
                runRepository,
                jobRepository,
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
        var run = run(RunStatus.RUNNING, originalExpiry);
        run.setStartedAt(originalExpiry.minusMinutes(10));
        run.setCurrentLeaseReservedMinutes(15L);
        run.setLeaseSequence(1L);

        when(runRepository.findLeaseEnforcementCandidatesForUpdate(anyCollection(), any(), any()))
                .thenReturn(List.of(run));
        when(runRepository.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));
        when(quotaAccountingService.getLeaseMinutes()).thenReturn(15L);
        when(quotaAccountingService.reserveAdditionalLease(
                eq(run.getProfile().getId()),
                same(run),
                eq(originalExpiry.plusMinutes(15)),
                anyString()
        )).thenReturn(15L);

        leaseWorker.enforceDueLeases("test");

        assertThat(run.getStatus()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.getActiveLeaseExpiresAt()).isEqualTo(originalExpiry.plusMinutes(15));
        assertThat(run.getCurrentLeaseReservedMinutes()).isEqualTo(30L);
        assertThat(run.getLeaseSequence()).isEqualTo(2L);
        assertThat(run.getLastLeaseRenewalError()).isNull();
        verify(nomadJobControlClient, never()).stopJob(anyString());
    }

    @Test
    void stopsNomadAndTimesOutRunWhenRenewalQuotaIsUnavailable() {
        var originalExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30);
        var run = run(RunStatus.RUNNING, originalExpiry);
        run.setNomadJobId("nomad-run");
        run.setStartedAt(originalExpiry.minusMinutes(15));
        run.setCurrentLeaseReservedMinutes(15L);
        run.setLeaseSequence(1L);

        when(runRepository.findLeaseEnforcementCandidatesForUpdate(anyCollection(), any(), any()))
                .thenReturn(List.of(run));
        when(runRepository.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));
        when(quotaAccountingService.getLeaseMinutes()).thenReturn(15L);
        when(quotaAccountingService.reserveAdditionalLease(any(), same(run), any(), anyString()))
                .thenThrow(new InsufficientQuotaException(0, 15));

        leaseWorker.enforceDueLeases("test");

        assertThat(run.getStatus()).isEqualTo(RunStatus.TIMED_OUT);
        assertThat(run.getTerminalReason()).startsWith("InsufficientQuotaException");
        assertThat(run.getLeaseSettled()).isTrue();
        assertThat(run.getActiveLeaseExpiresAt()).isNull();
        assertThat(run.getCurrentLeaseReservedMinutes()).isZero();
        verify(nomadJobControlClient).stopJob("nomad-run");
        verify(quotaAccountingService).settleLeaseMinutes(same(run), eq(15L), eq(15L), anyString());
    }

    @Test
    void stopsNomadAndTimesOutRunWhenLeaseAlreadyExpired() {
        var expiredAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(5);
        var run = run(RunStatus.RUNNING, expiredAt);
        run.setNomadJobId("expired-nomad-run");
        run.setStartedAt(expiredAt.minusMinutes(15));

        when(runRepository.findLeaseEnforcementCandidatesForUpdate(anyCollection(), any(), any()))
                .thenReturn(List.of(run));
        when(runRepository.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));

        leaseWorker.enforceDueLeases("test");

        assertThat(run.getStatus()).isEqualTo(RunStatus.TIMED_OUT);
        assertThat(run.getTerminalReason()).isEqualTo("LEASE_EXPIRED");
        assertThat(run.getLeaseSettled()).isTrue();
        verify(quotaAccountingService, never()).reserveAdditionalLease(any(), any(), any(), anyString());
        verify(nomadJobControlClient).stopJob("expired-nomad-run");
        verify(quotaAccountingService).settleLeaseMinutes(same(run), eq(15L), eq(15L), anyString());
    }

    private Run run(RunStatus status, OffsetDateTime activeLeaseExpiresAt) {
        var profile = Profile.builder()
                .id(UUID.randomUUID())
                .build();
        var job = Job.builder()
                .id(UUID.randomUUID())
                .profile(profile)
                .status(status)
                .build();
        return Run.builder()
                .id(UUID.randomUUID())
                .job(job)
                .profile(profile)
                .runNumber(1)
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
