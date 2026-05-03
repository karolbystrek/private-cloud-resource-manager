package com.pcrm.backend.jobs.service;

import com.pcrm.backend.auth.domain.CustomUserDetails;
import com.pcrm.backend.exception.ResourceNotFoundException;
import com.pcrm.backend.jobs.domain.RunStatus;
import com.pcrm.backend.jobs.dto.JobDetailsResponse;
import com.pcrm.backend.jobs.dto.JobHistoryItemResponse;
import com.pcrm.backend.jobs.dto.JobsPageResponse;
import com.pcrm.backend.jobs.repository.JobRepository;
import com.pcrm.backend.user.UserRole;
import com.pcrm.backend.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final JobRepository jobRepository;
    private final ProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public JobsPageResponse listUserJobs(
            UUID userId,
            int page,
            int size,
            Sort.Direction sortDirection,
            List<RunStatus> statusFilters
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(sortDirection, "createdAt"));
        var jobsPage = statusFilters == null || statusFilters.isEmpty()
                ? jobRepository.findByProfile_Id(userId, pageable)
                : jobRepository.findByProfile_IdAndStatusIn(userId, statusFilters, pageable);

        var jobs = jobsPage.getContent()
                .stream()
                .map(JobHistoryItemResponse::from)
                .toList();

        return new JobsPageResponse(
                jobs,
                jobsPage.getNumber(),
                jobsPage.getSize(),
                jobsPage.getTotalElements(),
                jobsPage.getTotalPages(),
                jobsPage.hasNext(),
                jobsPage.hasPrevious(),
                sortDirection.name().toLowerCase(Locale.ROOT)
        );
    }

    @Transactional(readOnly = true)
    public JobDetailsResponse getJobDetails(UUID jobId, CustomUserDetails principal) {
        var job = principal.role() == UserRole.ADMIN
                ? jobRepository.findById(jobId)
                : jobRepository.findByIdAndProfile_Id(jobId, principal.id());

        var j = job.orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId.toString()));
        var email = profileRepository.findEmailForAuthUser(j.getProfile().getId()).orElse(principal.email());
        return JobDetailsResponse.from(j, email);
    }

    @Transactional(readOnly = true)
    public UUID getJobOwnerId(UUID jobId) {
        return jobRepository.findById(jobId)
                .map(job -> job.getProfile().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Job", "id", jobId.toString()));
    }
}
