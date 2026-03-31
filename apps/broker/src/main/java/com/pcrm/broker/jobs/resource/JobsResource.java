package com.pcrm.broker.jobs.resource;

import com.pcrm.broker.auth.domain.CustomUserDetails;
import com.pcrm.broker.jobs.dto.JobSubmissionRequest;
import com.pcrm.broker.jobs.dto.JobSubmissionResponse;
import com.pcrm.broker.jobs.service.JobSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        var jobId = jobSubmissionService.submitJob(principal.user().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new JobSubmissionResponse(jobId));
    }
}
