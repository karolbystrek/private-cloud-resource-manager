package com.pcrm.backend.jobs.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@Profile("test")
public class InMemoryJobLogObjectStorage implements JobLogObjectStorage {

    private final ConcurrentMap<String, String> chunks = new ConcurrentHashMap<>();

    @Override
    public void putLogChunk(String objectKey, String content) {
        chunks.put(objectKey, content);
    }

    @Override
    public String getLogChunk(String objectKey) {
        return chunks.getOrDefault(objectKey, "");
    }
}
