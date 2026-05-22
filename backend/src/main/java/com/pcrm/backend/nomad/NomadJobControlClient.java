package com.pcrm.backend.nomad;

public interface NomadJobControlClient {

    void stopJob(String nomadJobId);
}
