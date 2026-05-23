package com.pcrm.backend.jobs.service;

import com.pcrm.backend.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class S3JobLogObjectStorage implements JobLogObjectStorage {

    private final StorageService storageService;

    @Override
    public void putLogChunk(String objectKey, String content) {
        storageService.putTextObject(objectKey, content);
    }

    @Override
    public String getLogChunk(String objectKey) {
        return storageService.getTextObject(objectKey);
    }
}
