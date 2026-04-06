package com.pcrm.backend.jobs.dto;

import java.util.List;

public record JobsPageResponse(
        List<JobHistoryItemResponse> jobs,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious,
        String sort
) {
}
