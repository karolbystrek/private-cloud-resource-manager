package com.pcrm.backend;

import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.nomad.NomadDispatchClient;
import com.pcrm.backend.nomad.NomadLogsClient;
import com.pcrm.backend.nomad.NomadNodeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@Profile("test")
class TestNomadConfig {

    @Bean
    NomadDispatchClient nomadDispatchClient() {
        return new NomadDispatchClient() {
            @Override
            public void dispatchJob(UUID userId, UUID jobId, JobSubmissionRequest request) {
            }
        };
    }

    @Bean
    NomadLogsClient nomadLogsClient() {
        return new NomadLogsClient() {
            @Override
            public List<NomadAllocationSnapshot> listJobAllocations(String nomadJobId) {
                return List.of();
            }

            @Override
            public void streamAllocationLogs(
                    String allocationId,
                    String streamType,
                    boolean follow,
                    long offset,
                    Consumer<NomadLogFrame> frameConsumer
            ) {
            }
        };
    }

    @Bean
    NomadNodeClient nomadNodeClient() {
        return List::of;
    }
}
