package com.pcrm.backend.nomad.stream.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NomadEventPayload(
        @JsonProperty("Node") NomadEventNode node,
        @JsonProperty("Allocation") NomadEventAllocation allocation,
        @JsonProperty("Job") NomadEventJob job
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NomadEventJob(
            @JsonProperty("ID") String id,
            @JsonProperty("Status") String status,
            @JsonProperty("Stop") Boolean stop
    ) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NomadEventNode(
            @JsonProperty("ID") String id,
            @JsonProperty("Status") String status,
            @JsonProperty("StatusDescription") String statusDescription,
            @JsonProperty("Drain") Boolean drain,
            @JsonProperty("SchedulingEligibility") String schedulingEligibility,
            @JsonProperty("Meta") Map<String, String> meta
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NomadEventAllocation(
            @JsonProperty("ID") String id,
            @JsonProperty("JobID") String jobId,
            @JsonProperty("ClientStatus") String clientStatus,
            @JsonProperty("TaskStates") Map<String, TaskState> taskStates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskState(
            @JsonProperty("State") String state,
            @JsonProperty("Failed") Boolean failed,
            @JsonProperty("Events") java.util.List<TaskEvent> events
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskEvent(
            @JsonProperty("Type") String type,
            @JsonProperty("Message") String message,
            @JsonProperty("DisplayMessage") String displayMessage,
            @JsonProperty("Details") Map<String, String> details
    ) {}
}
