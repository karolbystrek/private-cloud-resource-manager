package com.pcrm.backend.jobs.service;

import com.pcrm.backend.exception.InvalidIdempotencyKeyException;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class JobSubmissionIdempotencyService {

    public JobSubmissionIdempotency build(String idempotencyKey, JobSubmissionRequest request) {
        return new JobSubmissionIdempotency(
                normalizeIdempotencyKey(idempotencyKey),
                calculateSubmissionFingerprint(request)
        );
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidIdempotencyKeyException();
        }
        try {
            return UUID.fromString(idempotencyKey.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new InvalidIdempotencyKeyException();
        }
    }

    private String calculateSubmissionFingerprint(JobSubmissionRequest request) {
        var canonicalPayload = new StringBuilder()
                .append(request.dockerImage()).append('\u001F')
                .append(request.executionCommand()).append('\u001F')
                .append(request.reqCpuCores()).append('\u001F')
                .append(request.reqRamGb()).append('\u001F');

        var sortedEnvVars = new TreeMap<String, String>();
        if (request.envVars() != null) {
            sortedEnvVars.putAll(request.envVars());
        }

        sortedEnvVars.forEach((key, value) ->
                canonicalPayload
                        .append(key)
                        .append('\u001E')
                        .append(value)
                        .append('\u001F')
        );

        return sha256Hex(canonicalPayload.toString());
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
}
