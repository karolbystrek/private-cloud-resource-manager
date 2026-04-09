package com.pcrm.backend.storage.dto;

public record PresignedUrlResponse(
        String url,
        String objectKey,
        int expiresInSec
) {
}
