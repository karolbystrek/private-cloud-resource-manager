package com.pcrm.backend.storage.resource;

import com.pcrm.backend.jobs.service.JobQueryService;
import com.pcrm.backend.storage.dto.PresignedUrlResponse;
import com.pcrm.backend.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Profile("!test")
@RequestMapping("/internal/jobs")
public class InternalStorageResource {

    private final StorageService storageService;
    private final JobQueryService jobQueryService;

    @GetMapping("/{jobId}/artifact-upload-url")
    @PreAuthorize("hasRole('ADMIN')")
    public PresignedUrlResponse getArtifactUploadUrl(@PathVariable UUID jobId) {
        var ownerId = jobQueryService.getJobOwnerId(jobId);
        var objectKey = storageService.buildArtifactObjectKey(ownerId, jobId);
        var uploadUrl = storageService.generatePresignedUploadUrl(ownerId, jobId);
        return new PresignedUrlResponse(uploadUrl, objectKey, storageService.getUploadTtlSec());
    }
}
