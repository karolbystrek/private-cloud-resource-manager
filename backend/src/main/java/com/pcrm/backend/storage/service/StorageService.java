package com.pcrm.backend.storage.service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner s3Presigner;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.presign.upload-ttl-sec}")
    private int uploadTtlSec;

    @Value("${app.storage.s3.presign.download-ttl-sec}")
    private int downloadTtlSec;

    @PostConstruct
    void initializeBucket() {
        try {
            jdbcTemplate.update(
                    "INSERT INTO storage.buckets (id, name, public) VALUES (?, ?, false) ON CONFLICT (id) DO NOTHING",
                    bucket,
                    bucket);
            log.info("Ensured storage bucket '{}' exists in Supabase.", bucket);
        } catch (Exception e) {
            log.warn(
                    "Failed to automatically create storage bucket '{}'. Ensure it exists in Supabase Studio: {}",
                    bucket,
                    e.getMessage());
        }
    }

    public String generatePresignedUploadUrl(UUID userId, UUID jobId) {
        return generatePresignedUrl(userId, jobId, true, uploadTtlSec);
    }

    public String generatePresignedDownloadUrl(UUID userId, UUID jobId) {
        return generatePresignedUrl(userId, jobId, false, downloadTtlSec);
    }

    public String buildArtifactObjectKey(UUID userId, UUID jobId) {
        return "artifacts/" + userId + "/" + jobId + "/output.zip";
    }

    public int getUploadTtlSec() {
        return uploadTtlSec;
    }

    public int getDownloadTtlSec() {
        return downloadTtlSec;
    }

    private String generatePresignedUrl(UUID userId, UUID jobId, boolean upload, int expirySeconds) {
        if (expirySeconds <= 0) {
            throw new IllegalStateException("Presigned URL expiry must be greater than zero");
        }

        String objectKey = buildArtifactObjectKey(userId, jobId);
        var ttl = Duration.ofSeconds(expirySeconds);

        try {
            if (upload) {
                var presigned =
                        s3Presigner.presignPutObject(
                                PutObjectPresignRequest.builder()
                                        .signatureDuration(ttl)
                                        .putObjectRequest(
                                                PutObjectRequest.builder()
                                                        .bucket(bucket)
                                                        .key(objectKey)
                                                        .build())
                                        .build());
                return presigned.url().toString();
            }
            var presigned =
                    s3Presigner.presignGetObject(
                            GetObjectPresignRequest.builder()
                                    .signatureDuration(ttl)
                                    .getObjectRequest(
                                            GetObjectRequest.builder()
                                                    .bucket(bucket)
                                                    .key(objectKey)
                                                    .build())
                                    .build());
            return presigned.url().toString();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to generate presigned URL for object " + objectKey,
                    exception);
        }
    }
}
