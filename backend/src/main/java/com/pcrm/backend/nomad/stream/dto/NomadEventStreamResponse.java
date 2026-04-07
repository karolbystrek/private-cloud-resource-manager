package com.pcrm.backend.nomad.stream.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NomadEventStreamResponse(
        @JsonProperty("Index") Long index,
        @JsonProperty("Events") List<NomadEvent> events
) {
}
