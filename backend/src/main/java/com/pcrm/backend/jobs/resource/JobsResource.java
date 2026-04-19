package com.pcrm.backend.jobs.resource;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.jobs.dto.JobDetailsResponse;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.dto.JobSubmissionResponse;
import com.pcrm.backend.jobs.dto.JobsPageResponse;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.service.JobLogStreamType;
import com.pcrm.backend.jobs.service.JobLogsService;
import com.pcrm.backend.jobs.service.JobQueryService;
import com.pcrm.backend.jobs.service.JobSubmissionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/jobs")
public class JobsResource {

    private final JobSubmissionService jobSubmissionService;
    private final JobQueryService jobQueryService;
    private final JobLogsService jobLogsService;

    @GetMapping
    public JobsPageResponse listJobs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(name = "status", required = false) List<JobStatus> statuses,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        var sortDirection = parseSortDirection(sort);
        return jobQueryService.listUserJobs(principal.user().getId(), page, size, sortDirection, statuses);
    }

    @GetMapping("/{id}")
    public JobDetailsResponse getJobDetails(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return jobQueryService.getJobDetails(id, principal);
    }

    @GetMapping(value = "/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobLogs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "stdout") String stream,
            @RequestParam(defaultValue = "0") @Min(0) long offset,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        var streamType = JobLogStreamType.from(stream);
        return jobLogsService.streamJobLogs(id, principal, streamType, offset);
    }

    @PostMapping
    public ResponseEntity<JobSubmissionResponse> submitJob(
            @RequestBody @Valid JobSubmissionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        log.info("Received job submission request from user {}", principal.user().getUsername());
        var result = jobSubmissionService.submitJob(principal.user().getId(), request, idempotencyKey);
        var status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(new JobSubmissionResponse(result.jobId()));
    }

    private Sort.Direction parseSortDirection(String sort) {
        if ("asc".equalsIgnoreCase(sort)) {
            return Sort.Direction.ASC;
        }
        if ("desc".equalsIgnoreCase(sort)) {
            return Sort.Direction.DESC;
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid sort value. Allowed values: asc, desc."
        );
    }
}
