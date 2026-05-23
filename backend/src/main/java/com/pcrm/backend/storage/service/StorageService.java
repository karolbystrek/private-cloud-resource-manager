package com.pcrm.backend.storage.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@Profile("!test")
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.presign.upload-ttl-sec}")
    private int uploadTtlSec;

    @Value("${app.storage.s3.presign.download-ttl-sec}")
    private int downloadTtlSec;

    @PostConstruct
    void initializeBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Object storage bucket '{}' exists.", bucket);
        } catch (S3Exception ex) {
            if (ex.statusCode() != 404) {
                throw new IllegalStateException("Failed to check object storage bucket " + bucket, ex);
            }

            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created object storage bucket '{}'.", bucket);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize object storage bucket " + bucket, ex);
        }
    }

    public String generatePresignedUploadUrl(UUID userId, UUID jobId) {
        return generatePresignedUploadUrl(buildArtifactObjectKey(userId, jobId));
    }

    public String generatePresignedDownloadUrl(UUID userId, UUID jobId) {
        return generatePresignedDownloadUrl(buildArtifactObjectKey(userId, jobId));
    }

    public String generatePresignedUploadUrl(String objectKey) {
        return generatePresignedUrl(objectKey, true, uploadTtlSec);
    }

    public String generatePresignedDownloadUrl(String objectKey) {
        return generatePresignedUrl(objectKey, false, downloadTtlSec);
    }

    public String buildArtifactObjectKey(UUID userId, UUID jobId) {
        return "artifacts/" + userId + "/" + jobId + "/output.zip";
    }

    public StoredObjectMetadata getObjectMetadata(String objectKey) {
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return new StoredObjectMetadata(true, response.contentLength(), response.checksumSHA256());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return StoredObjectMetadata.missing();
            }
            throw new IllegalStateException("Failed to check object " + objectKey, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to check object " + objectKey, ex);
        }
    }

    public int getUploadTtlSec() {
        return uploadTtlSec;
    }

    public int getDownloadTtlSec() {
        return downloadTtlSec;
    }

    private String generatePresignedUrl(String objectKey, boolean upload, int expirySeconds) {
        if (expirySeconds <= 0) {
            throw new IllegalStateException("Presigned URL expiry must be greater than zero");
        }

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

    public record StoredObjectMetadata(boolean exists, Long sizeBytes, String checksum) {

        static StoredObjectMetadata missing() {
            return new StoredObjectMetadata(false, null, null);
        }
    }
}
