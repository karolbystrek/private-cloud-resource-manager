package com.pcrm.backend.jobs.service;

public interface JobLogObjectStorage {

    void putLogChunk(String objectKey, String content);

    String getLogChunk(String objectKey);
}
