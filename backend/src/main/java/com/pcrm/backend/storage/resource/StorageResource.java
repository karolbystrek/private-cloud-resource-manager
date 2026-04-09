package com.pcrm.backend.storage.resource;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.jobs.service.JobQueryService;
import com.pcrm.backend.storage.dto.PresignedUrlResponse;
import com.pcrm.backend.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Profile("!test")
@RequestMapping("/jobs")
public class StorageResource {

    private final StorageService storageService;
    private final JobQueryService jobQueryService;

    @GetMapping("/{jobId}/artifact-download-url")
    public PresignedUrlResponse getArtifactDownloadUrl(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        var jobDetails = jobQueryService.getJobDetails(jobId, principal);
        var ownerId = jobDetails.userId();
        var objectKey = storageService.buildArtifactObjectKey(ownerId, jobId);
        var downloadUrl = storageService.generatePresignedDownloadUrl(ownerId, jobId);
        return new PresignedUrlResponse(downloadUrl, objectKey, storageService.getDownloadTtlSec());
    }
}
