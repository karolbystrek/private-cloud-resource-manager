package com.pcrm.backend.storage.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.presign.upload-ttl-sec}")
    private int uploadTtlSec;

    @Value("${app.storage.s3.presign.download-ttl-sec}")
    private int downloadTtlSec;

    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created object storage bucket {}", bucket);
            } else {
                log.info("Object storage bucket {} is ready", bucket);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to initialize object storage bucket " + bucket, exception);
        }
    }

    public String generatePresignedUploadUrl(UUID userId, UUID jobId) {
        return generatePresignedUrl(userId, jobId, Method.PUT, uploadTtlSec);
    }

    public String generatePresignedDownloadUrl(UUID userId, UUID jobId) {
        return generatePresignedUrl(userId, jobId, Method.GET, downloadTtlSec);
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

    private String generatePresignedUrl(UUID userId, UUID jobId, Method method, int expirySeconds) {
        if (expirySeconds <= 0) {
            throw new IllegalStateException("Presigned URL expiry must be greater than zero");
        }

        String objectKey = buildArtifactObjectKey(userId, jobId);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(method)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to generate presigned URL for object " + objectKey,
                    exception
            );
        }
    }
}
