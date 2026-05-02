package com.pcrm.backend.events.service;

import com.pcrm.backend.events.domain.OutboxMessage;
import com.pcrm.backend.events.repository.OutboxClaimRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final OutboxClaimRepository outboxClaimRepository;
    private final OutboxHandlerRegistry handlerRegistry;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.events.outbox.enabled:true}")
    private boolean enabled;

    @Value("${app.events.outbox.batch-size:25}")
    private int batchSize;

    @Value("${app.events.outbox.claim-timeout-ms:60000}")
    private long claimTimeoutMs;

    @Value("${app.events.outbox.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${app.events.outbox.worker-name:}")
    private String configuredWorkerName;

    private String workerName;

    @PostConstruct
    void initializeWorkerName() {
        if (configuredWorkerName == null || configuredWorkerName.isBlank()) {
            workerName = "backend-outbox-" + UUID.randomUUID();
            return;
        }
        workerName = configuredWorkerName;
    }

    @Scheduled(fixedDelayString = "${app.events.outbox.poll-interval-ms:1000}")
    public void poll() {
        if (!enabled || batchSize <= 0) {
            return;
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var messages = outboxClaimRepository.claimAvailable(
                batchSize,
                now,
                Duration.ofMillis(claimTimeoutMs),
                workerName
        );

        for (OutboxMessage message : messages) {
            process(message);
        }
    }

    private void process(OutboxMessage message) {
        try {
            transactionTemplate.executeWithoutResult(_ -> {
                var handler = handlerRegistry.findHandler(message.getTopic());
                if (handler.isPresent()) {
                    handler.get().handle(message);
                } else {
                    log.debug("No outbox handler registered for topic {}, marking message {} as published",
                            message.getTopic(), message.getId());
                }
                outboxClaimRepository.markPublished(message.getId(), OffsetDateTime.now(ZoneOffset.UTC));
            });
        } catch (Exception ex) {
            var retryAt = OffsetDateTime.now(ZoneOffset.UTC).plus(Duration.ofMillis(retryDelayMs));
            outboxClaimRepository.markFailed(message.getId(), retryAt, summarize(ex));
            log.error("Outbox message {} failed for topic {}", message.getId(), message.getTopic(), ex);
        }
    }

    private String summarize(Exception ex) {
        var message = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
        if (message.length() <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_LENGTH);
    }
}
