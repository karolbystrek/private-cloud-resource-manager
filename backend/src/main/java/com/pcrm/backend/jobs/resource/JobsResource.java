package com.pcrm.backend.jobs.resource;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.jobs.domain.JobStatus;
import com.pcrm.backend.jobs.dto.JobDetailsResponse;
import com.pcrm.backend.jobs.dto.JobLogsResponse;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.dto.JobSubmissionResponse;
import com.pcrm.backend.jobs.dto.JobsPageResponse;
import com.pcrm.backend.jobs.service.JobDetailsStreamService;
import com.pcrm.backend.jobs.service.JobCancellationService;
import com.pcrm.backend.jobs.service.JobLogStreamType;
import com.pcrm.backend.jobs.service.JobLogsService;
import com.pcrm.backend.jobs.service.JobQueryService;
import com.pcrm.backend.jobs.service.JobSubmissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
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
    private final JobDetailsStreamService jobDetailsStreamService;
    private final JobCancellationService jobCancellationService;

    @GetMapping
    public JobsPageResponse listJobs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(name = "status", required = false) List<JobStatus> statuses,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        var sortDirection = parseSortDirection(sort);
        return jobQueryService.listUserJobs(principal.id(), page, size, sortDirection, statuses);
    }

    @GetMapping("/{id}")
    public JobDetailsResponse getJobDetails(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return jobQueryService.getJobDetails(id, principal);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobDetails(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return jobDetailsStreamService.streamJobDetails(id, principal);
    }

    @GetMapping("/{id}/logs")
    public JobLogsResponse getJobLogs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "stdout") String stream,
            @RequestParam(defaultValue = "1048576") @Min(1) @Max(10_485_760) long limitBytes,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        var streamType = JobLogStreamType.from(stream);
        return jobLogsService.getJobLogs(id, principal, streamType, limitBytes);
    }

    @PostMapping
    public ResponseEntity<JobSubmissionResponse> submitJob(
            @RequestBody @Valid JobSubmissionRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        log.info("Received job submission request from user {}", principal.id());
        var result = jobSubmissionService.submitJob(principal.id(), request, idempotencyKey);
        var status = result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(new JobSubmissionResponse(result.jobId()));
    }

    @PostMapping("/{id}/cancel")
    public JobDetailsResponse cancelJob(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return jobCancellationService.cancelJob(id, principal);
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
