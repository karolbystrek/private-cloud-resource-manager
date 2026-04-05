package com.pcrm.backend.nomad;

import java.util.List;

public interface NomadNodeClient {

    List<NomadNodeSnapshot> fetchClientNodes();
}
