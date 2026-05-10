package com.pcrm.backend.storage.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@Profile("!test")
public class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Presigner storageS3Presigner(
            @Value("${app.storage.s3.endpoint}") String endpoint,
            @Value("${app.storage.s3.access-key}") String accessKey,
            @Value("${app.storage.s3.secret-key}") String secretKey,
            @Value("${app.storage.s3.region}") String regionId,
            @Value("${app.storage.s3.path-style-access:true}") boolean pathStyleAccess
    ) {
        var creds =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        var normalized = URI.create(endpoint.trim()).normalize();
        var region = Region.of(regionId.strip());
        var s3Cfg = S3Configuration.builder().pathStyleAccessEnabled(pathStyleAccess).build();

        return S3Presigner.builder()
                .credentialsProvider(creds)
                .region(region)
                .endpointOverride(normalized)
                .serviceConfiguration(s3Cfg)
                .build();
    }
}
