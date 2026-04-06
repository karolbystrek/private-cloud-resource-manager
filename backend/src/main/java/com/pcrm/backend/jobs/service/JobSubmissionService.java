package com.pcrm.backend.jobs.service;

import com.pcrm.backend.creditregistry.domain.CreditRegistryEntry;
import com.pcrm.backend.creditregistry.repository.CreditRegistryRepository;
import com.pcrm.backend.exception.InsufficientFundsException;
import com.pcrm.backend.exception.NomadDispatchException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.user.repository.UserRepository;
import com.pcrm.backend.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static com.pcrm.backend.creditregistry.domain.TransactionType.LEASE_DEDUCTION;
import static com.pcrm.backend.creditregistry.domain.TransactionType.LEASE_REFUND;

@Slf4j
@Service
public class JobSubmissionService {

    private final NomadDispatchClient nomadDispatchClient;
    private final WalletRepository walletRepository;
    private final JobRepository jobRepository;
    private final CreditRegistryRepository creditRegistryRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final TransactionTemplate submissionTransactionTemplate;
    private final TransactionTemplate compensationTransactionTemplate;

    public JobSubmissionService(
            NomadDispatchClient nomadDispatchClient,
            WalletRepository walletRepository,
            JobRepository jobRepository,
            CreditRegistryRepository creditRegistryRepository,
            UserRepository userRepository,
            PricingService pricingService,
            PlatformTransactionManager transactionManager
    ) {
        this.nomadDispatchClient = nomadDispatchClient;
        this.walletRepository = walletRepository;
        this.jobRepository = jobRepository;
        this.creditRegistryRepository = creditRegistryRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;

        this.submissionTransactionTemplate = new TransactionTemplate(transactionManager);

        DefaultTransactionDefinition compensationDefinition = new DefaultTransactionDefinition();
        compensationDefinition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.compensationTransactionTemplate = new TransactionTemplate(transactionManager, compensationDefinition);
    }

    public PreparedJobSubmission prepareSubmission(UUID userId, JobSubmissionRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        var wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "userId", userId.toString()));

        long initialLeaseCost = pricingService.calculateInitialLeaseCost(request);
        if (wallet.getBalance() < initialLeaseCost) {
            throw new InsufficientFundsException(wallet.getBalance(), initialLeaseCost);
        }

        wallet.setBalance(wallet.getBalance() - initialLeaseCost);

        var job = Job.builder()
                .user(user)
                .nodeId(null)
                .status(JobStatus.PENDING)
                .dockerImage(request.dockerImage())
                .executionCommand(request.executionCommand())
                .reqCpuCores(request.reqCpuCores())
                .reqRamGb(request.reqRamGb())
                .totalCostCredits(initialLeaseCost)
                .build();

        var savedJob = jobRepository.save(job);

        var creditRegistryEntry = CreditRegistryEntry.builder()
                .wallet(wallet)
                .job(savedJob)
                .transactionType(LEASE_DEDUCTION)
                .amountCredits(-initialLeaseCost)
                .description("Initial 15-minute lease deduction")
                .build();

        creditRegistryRepository.save(creditRegistryEntry);
        walletRepository.save(wallet);

        log.debug("Prepared job submission for user {}: jobId#{}", user.getUsername(), savedJob.getId());
        return new PreparedJobSubmission(savedJob.getId(), userId, initialLeaseCost);
    }

    public UUID submitJob(UUID userId, JobSubmissionRequest request) {
        var preparedJobSubmission = submissionTransactionTemplate.execute(
                _ -> prepareSubmission(userId, request)
        );

        if (preparedJobSubmission == null) {
            throw new IllegalStateException("Failed to prepare job submission");
        }

        try {
            nomadDispatchClient.dispatchJob(userId, preparedJobSubmission.jobId(), request);
        } catch (NomadDispatchException ex) {
            compensationTransactionTemplate.executeWithoutResult(_ ->
                    compensateFailedDispatch(preparedJobSubmission));
            throw ex;
        }

        return preparedJobSubmission.jobId();
    }

    public void compensateFailedDispatch(PreparedJobSubmission preparedJobSubmission) {
        var wallet = walletRepository.findByUserId(preparedJobSubmission.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "userId", preparedJobSubmission.userId().toString()));

        var job = jobRepository.findById(preparedJobSubmission.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", preparedJobSubmission.jobId()));

        wallet.setBalance(wallet.getBalance() + preparedJobSubmission.initialLeaseCost());
        job.setStatus(JobStatus.FAILED);

        var creditRegistryEntry = CreditRegistryEntry.builder()
                .wallet(wallet)
                .job(job)
                .transactionType(LEASE_REFUND)
                .amountCredits(preparedJobSubmission.initialLeaseCost())
                .description("Nomad dispatch failed, initial lease refunded")
                .build();

        creditRegistryRepository.save(creditRegistryEntry);
        walletRepository.save(wallet);
        jobRepository.save(job);
        log.debug("Compensated failed dispatch for jobId#{}", preparedJobSubmission.jobId());
    }
}
