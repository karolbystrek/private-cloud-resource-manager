package com.pcrm.backend.jobs.service;

import com.pcrm.backend.creditregistry.domain.CreditRegistryEntry;
import com.pcrm.backend.creditregistry.repository.CreditRegistryRepository;
import com.pcrm.backend.exception.IdempotencyKeyConflictException;
import com.pcrm.backend.exception.InsufficientFundsException;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.Job;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.user.User;
import com.pcrm.backend.user.repository.UserRepository;
import com.pcrm.backend.wallet.domain.Wallet;
import com.pcrm.backend.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

import static com.pcrm.backend.creditregistry.domain.TransactionType.LEASE_DEDUCTION;
import static com.pcrm.backend.creditregistry.domain.TransactionType.LEASE_REFUND;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSubmissionPersistenceService {

    private final WalletRepository walletRepository;
    private final JobRepository jobRepository;
    private final CreditRegistryRepository creditRegistryRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;

    public PreparedJobSubmission prepareSubmission(
            UUID userId,
            JobSubmissionRequest request,
            JobSubmissionIdempotency idempotency
    ) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        var wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", "userId", userId.toString()));

        var replayedSubmission = findReplayedSubmission(userId, idempotency, user.getUsername());
        if (replayedSubmission.isPresent()) {
            return replayedSubmission.get();
        }

        var initialLeaseCost = reserveInitialLeaseCost(wallet, request);
        var savedJob = createJob(user, request, idempotency, initialLeaseCost);

        creditRegistryRepository.save(buildLeaseDeductionEntry(wallet, savedJob, initialLeaseCost));
        walletRepository.save(wallet);

        log.debug("Prepared job submission for user {}: jobId#{}", user.getUsername(), savedJob.getId());
        return PreparedJobSubmission.created(savedJob.getId(), userId, initialLeaseCost);
    }

    public void compensateFailedDispatch(PreparedJobSubmission preparedJobSubmission) {
        var wallet = walletRepository.findByUserId(preparedJobSubmission.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Wallet",
                        "userId",
                        preparedJobSubmission.userId().toString()
                ));

        var job = jobRepository.findById(preparedJobSubmission.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", preparedJobSubmission.jobId()));

        wallet.setBalance(wallet.getBalance() + preparedJobSubmission.initialLeaseCost());
        job.setStatus(JobStatus.FAILED);

        creditRegistryRepository.save(buildLeaseRefundEntry(wallet, job, preparedJobSubmission.initialLeaseCost()));
        walletRepository.save(wallet);
        jobRepository.save(job);

        log.debug("Compensated failed dispatch for jobId#{}", preparedJobSubmission.jobId());
    }

    private Optional<PreparedJobSubmission> findReplayedSubmission(
            UUID userId,
            JobSubmissionIdempotency idempotency,
            String username
    ) {
        var existingJob = jobRepository.findByUser_IdAndIdempotencyKey(userId, idempotency.key());
        if (existingJob.isEmpty()) {
            return Optional.empty();
        }

        var persistedJob = existingJob.get();
        if (!Objects.equals(persistedJob.getSubmissionFingerprint(), idempotency.fingerprint())) {
            throw new IdempotencyKeyConflictException();
        }

        log.debug("Replayed idempotent job submission for user {}: jobId#{}", username, persistedJob.getId());
        return Optional.of(PreparedJobSubmission.replayed(persistedJob.getId(), userId));
    }

    private long reserveInitialLeaseCost(Wallet wallet, JobSubmissionRequest request) {
        var initialLeaseCost = pricingService.calculateInitialLeaseCost(request);
        if (wallet.getBalance() < initialLeaseCost) {
            throw new InsufficientFundsException(wallet.getBalance(), initialLeaseCost);
        }

        wallet.setBalance(wallet.getBalance() - initialLeaseCost);
        return initialLeaseCost;
    }

    private Job createJob(
            User user,
            JobSubmissionRequest request,
            JobSubmissionIdempotency idempotency,
            long initialLeaseCost
    ) {
        var job = Job.builder()
                .user(user)
                .nodeId(null)
                .status(JobStatus.PENDING)
                .dockerImage(request.dockerImage())
                .executionCommand(request.executionCommand())
                .idempotencyKey(idempotency.key())
                .submissionFingerprint(idempotency.fingerprint())
                .reqCpuCores(request.reqCpuCores())
                .reqRamGb(request.reqRamGb())
                .totalCostCredits(initialLeaseCost)
                .build();

        return jobRepository.save(job);
    }

    private CreditRegistryEntry buildLeaseDeductionEntry(
            Wallet wallet,
            Job job,
            long initialLeaseCost
    ) {
        return CreditRegistryEntry.builder()
                .wallet(wallet)
                .job(job)
                .transactionType(LEASE_DEDUCTION)
                .amountCredits(-initialLeaseCost)
                .description("Initial 15-minute lease deduction")
                .build();
    }

    private CreditRegistryEntry buildLeaseRefundEntry(
            Wallet wallet,
            Job job,
            long initialLeaseCost
    ) {
        return CreditRegistryEntry.builder()
                .wallet(wallet)
                .job(job)
                .transactionType(LEASE_REFUND)
                .amountCredits(initialLeaseCost)
                .description("Nomad dispatch failed, initial lease refunded")
                .build();
    }
}
