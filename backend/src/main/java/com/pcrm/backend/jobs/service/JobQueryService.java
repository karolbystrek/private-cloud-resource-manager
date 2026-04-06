package com.pcrm.backend.jobs.service;

import com.pcrm.backend.jobs.dto.JobHistoryItemResponse;
import com.pcrm.backend.jobs.dto.JobsPageResponse;
import com.pcrm.backend.jobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobQueryService {

    private final JobRepository jobRepository;

    @Transactional(readOnly = true)
    public JobsPageResponse listUserJobs(UUID userId, int page, int size, Sort.Direction sortDirection) {
        var pageable = PageRequest.of(page, size, Sort.by(sortDirection, "createdAt"));
        var jobsPage = jobRepository.findByUser_Id(userId, pageable);

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
}
