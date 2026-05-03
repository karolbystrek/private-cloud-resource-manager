package com.pcrm.backend.nomad.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.jobs.repository.RunRepository;
import com.pcrm.backend.jobs.service.JobRunEventPublisher;
import com.pcrm.backend.nodes.repository.NodeRepository;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.nomad.NomadLogsClient;
import com.pcrm.backend.nomad.NomadNodeClient;
import com.pcrm.backend.nomad.http.NomadHttpDispatchClient;
import com.pcrm.backend.nomad.http.NomadHttpLogsClient;
import com.pcrm.backend.nomad.http.NomadHttpNodeClient;
import com.pcrm.backend.nomad.stream.NomadEventStreamListener;
import com.pcrm.backend.nomad.stream.NomadStreamCursorRepository;
import com.pcrm.backend.quota.service.QuotaAccountingService;
import com.pcrm.backend.storage.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(name = "app.nomad.enabled", havingValue = "true", matchIfMissing = true)
public class NomadIntegrationConfig {

    @Bean
    NomadDispatchClient nomadDispatchClient(
            @Value("${app.nomad.base-url}") String nomadBaseUrl,
            @Value("${app.nomad.job-template}") String jobTemplatePath,
            StorageService storageService
    ) throws IOException {
        return new NomadHttpDispatchClient(nomadBaseUrl, jobTemplatePath, storageService);
    }

    @Bean
    NomadLogsClient nomadLogsClient(
            @Value("${app.nomad.base-url}") String nomadBaseUrl,
            JsonMapper jsonMapper
    ) {
        return new NomadHttpLogsClient(nomadBaseUrl, jsonMapper);
    }

    @Bean
    NomadNodeClient nomadNodeClient(@Value("${app.nomad.base-url}") String nomadBaseUrl) {
        return new NomadHttpNodeClient(nomadBaseUrl);
    }

    @Bean
    NomadEventStreamListener nomadEventStreamListener(
            @Value("${app.nomad.base-url}") String nomadBaseUrl,
            NomadStreamCursorRepository cursorRepository,
            JobRepository jobRepository,
            RunRepository runRepository,
            NodeRepository nodeRepository,
            QuotaAccountingService quotaAccountingService,
            JobRunEventPublisher eventPublisher,
            JsonMapper jsonMapper,
            TransactionTemplate transactionTemplate
    ) {
        return new NomadEventStreamListener(
                nomadBaseUrl,
                cursorRepository,
                jobRepository,
                runRepository,
                nodeRepository,
                quotaAccountingService,
                eventPublisher,
                jsonMapper,
                transactionTemplate
        );
    }
}
