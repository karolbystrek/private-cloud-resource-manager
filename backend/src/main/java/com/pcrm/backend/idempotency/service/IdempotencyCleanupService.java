package com.pcrm.backend.idempotency.service;

import com.pcrm.backend.idempotency.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyCleanupService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    @Value("${app.idempotency.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${app.idempotency.cleanup.retention-hours:24}")
    private long retentionHours;

    @Scheduled(cron = "${app.idempotency.cleanup.cron:0 0 * * * *}")
    @Transactional
    public void cleanupIdempotencyRecords() {
        if (!cleanupEnabled) {
            return;
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var staleBefore = now.minusHours(retentionHours);
        var staleInProgress = idempotencyRecordRepository.countStaleInProgress(staleBefore);
        var deleted = idempotencyRecordRepository.deleteStaleRecords(staleBefore);

        if (staleInProgress > 0) {
            log.warn("Deleted stale IN_PROGRESS idempotency records: count={}", staleInProgress);
        }
        if (deleted > 0) {
            log.info("Cleaned stale idempotency records: deleted={}", deleted);
        }
    }
}
