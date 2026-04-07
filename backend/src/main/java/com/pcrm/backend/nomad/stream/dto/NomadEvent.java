package com.pcrm.backend.nomad.stream.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NomadEvent(
        @JsonProperty("Topic") String topic,
        @JsonProperty("Type") String type,
        @JsonProperty("Key") String key,
        @JsonProperty("Index") Long index,
        @JsonProperty("Payload") NomadEventPayload payload
) {
}
