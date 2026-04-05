package com.pcrm.backend.jobs.resource;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.jobs.dto.JobSubmissionRequest;
import com.pcrm.backend.jobs.dto.JobSubmissionResponse;
import com.pcrm.backend.jobs.service.JobSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/jobs")
public class JobsResource {

    private final JobSubmissionService jobSubmissionService;

    @PostMapping
    public ResponseEntity<JobSubmissionResponse> submitJob(
            @RequestBody @Valid JobSubmissionRequest request,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        log.info("Received job submission request from user {}", principal.user().getUsername());
        var jobId = jobSubmissionService.submitJob(principal.user().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new JobSubmissionResponse(jobId));
    }
}
