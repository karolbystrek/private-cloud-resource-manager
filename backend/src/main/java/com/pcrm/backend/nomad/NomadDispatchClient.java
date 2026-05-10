package com.pcrm.backend.nomad;

public interface NomadDispatchClient {

    NomadDispatchResult dispatchJob(NomadDispatchRequest request);
}
