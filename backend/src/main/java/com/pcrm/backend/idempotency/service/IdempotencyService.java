package com.pcrm.backend.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pcrm.backend.idempotency.domain.IdempotencyRecord;
import com.pcrm.backend.idempotency.domain.IdempotencyStatus;
import com.pcrm.backend.idempotency.exception.IdempotencyConflictException;
import com.pcrm.backend.idempotency.exception.IdempotencyInProgressException;
import com.pcrm.backend.idempotency.exception.InvalidIdempotencyKeyException;
import com.pcrm.backend.idempotency.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(5);
    private static final int MAX_KEY_LENGTH = 128;

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public <T> IdempotencyResult<T> execute(IdempotentWorkflow<T> workflow) {
        var idempotencyKey = normalizeIdempotencyKey(workflow.idempotencyKey());
        var requestFingerprint = calculateFingerprint(workflow.requestPayload());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var inserted = idempotencyRecordRepository.insertTenantlessInProgressIfAbsent(
                UUID.randomUUID(),
                workflow.actorType(),
                workflow.actorId(),
                workflow.workflow(),
                idempotencyKey,
                requestFingerprint,
                now.plus(DEFAULT_LOCK_DURATION),
                now
        );

        var record = idempotencyRecordRepository.findTenantlessForUpdate(
                        workflow.actorType(),
                        workflow.actorId(),
                        workflow.workflow(),
                        idempotencyKey
                )
                .orElseThrow(() -> new IllegalStateException("Idempotency record was not created"));

        if (!Objects.equals(record.getRequestFingerprint(), requestFingerprint)) {
            throw new IdempotencyConflictException();
        }

        if (record.getStatus() == IdempotencyStatus.COMPLETED) {
            return replayCompleted(workflow, record);
        }

        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS && inserted == 0) {
            throw new IdempotencyInProgressException();
        }

        if (record.getStatus() == IdempotencyStatus.FAILED_FINAL) {
            throw new IdempotencyConflictException();
        }

        if (record.getStatus() == IdempotencyStatus.FAILED_RETRYABLE && isLocked(record, now)) {
            throw new IdempotencyInProgressException();
        }

        record.setStatus(IdempotencyStatus.IN_PROGRESS);
        record.setLockedUntil(now.plus(DEFAULT_LOCK_DURATION));

        var response = workflow.action().apply(new IdempotencyContext(idempotencyKey, requestFingerprint));
        completeRecord(workflow, record, response);

        return new IdempotencyResult<>(
                response,
                false
        );
    }

    private <T> IdempotencyResult<T> replayCompleted(
            IdempotentWorkflow<T> workflow,
            IdempotencyRecord record
    ) {
        var response = workflow.completedResponseReader().apply(record.getResponseBody());
        return new IdempotencyResult<>(
                response,
                true
        );
    }

    private <T> void completeRecord(IdempotentWorkflow<T> workflow, IdempotencyRecord record, T response) {
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setLockedUntil(null);
        record.setResponseStatus(200);
        record.setResponseBody(toJson(response));
        record.setResourceType(workflow.resourceType());
        record.setResourceId(workflow.resourceId().apply(response));
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException();
        }

        var trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException();
        }

        try {
            return UUID.fromString(trimmed).toString();
        } catch (IllegalArgumentException ex) {
            throw new InvalidIdempotencyKeyException();
        }
    }

    private String calculateFingerprint(Object requestPayload) {
        try {
            var canonicalMapper = objectMapper.copy()
                    .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return sha256Hex(canonicalMapper.writeValueAsString(requestPayload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to canonicalize idempotent request payload", ex);
        }
    }

    private String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private JsonNode toJson(Object value) {
        return objectMapper.valueToTree(value);
    }

    private boolean isLocked(IdempotencyRecord record, OffsetDateTime now) {
        return record.getLockedUntil() != null && record.getLockedUntil().isAfter(now);
    }
}
