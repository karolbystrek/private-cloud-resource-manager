package com.pcrm.broker.jobs.service;

import com.pcrm.broker.creditregistry.domain.CreditRegistryEntry;
import com.pcrm.broker.creditregistry.repository.CreditRegistryRepository;
import com.pcrm.broker.exception.InsufficientFundsException;
import com.pcrm.broker.exception.NomadDispatchException;
import com.pcrm.broker.exception.ResourceNotFoundException;
import com.pcrm.broker.jobs.domain.Job;
import com.pcrm.broker.jobs.domain.JobStatus;
import com.pcrm.broker.jobs.dto.JobSubmissionRequest;
import com.pcrm.broker.jobs.repository.JobRepository;
import com.pcrm.broker.nomad.NomadDispatchClient;
import com.pcrm.broker.user.repository.UserRepository;
import com.pcrm.broker.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static com.pcrm.broker.creditregistry.domain.TransactionType.LEASE_DEDUCTION;
import static com.pcrm.broker.creditregistry.domain.TransactionType.LEASE_REFUND;

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
                .reqGpuCount(request.reqGpuCount())
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

        return new PreparedJobSubmission(savedJob.getId(), userId, initialLeaseCost);
    }

    public UUID submitJob(UUID userId, JobSubmissionRequest request) {
        var preparedJobSubmission = submissionTransactionTemplate.execute(status ->
                prepareSubmission(userId, request));

        if (preparedJobSubmission == null) {
            throw new IllegalStateException("Failed to prepare job submission");
        }

        try {
            nomadDispatchClient.dispatchJob(preparedJobSubmission.jobId(), request);
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
    }
}
