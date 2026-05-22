package com.pcrm.backend.nomad.http;

import com.pcrm.backend.exception.NomadJobControlException;
import com.pcrm.backend.nomad.NomadJobControlClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
public class NomadHttpJobControlClient implements NomadJobControlClient {

    private final RestClient restClient;

    public NomadHttpJobControlClient(String nomadBaseUrl) {
        this.restClient = RestClient.builder().baseUrl(nomadBaseUrl).build();
    }

    @Override
    public void stopJob(String nomadJobId) {
        try {
            restClient.delete()
                    .uri("/v1/job/{jobId}", nomadJobId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Requested Nomad stop for job {}", nomadJobId);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                log.info("Nomad job {} was already absent while enforcing lease", nomadJobId);
                return;
            }
            throw new NomadJobControlException("Failed to stop Nomad job " + nomadJobId, ex);
        } catch (Exception ex) {
            throw new NomadJobControlException("Failed to stop Nomad job " + nomadJobId + ": " + ex.getMessage(), ex);
        }
    }
}
