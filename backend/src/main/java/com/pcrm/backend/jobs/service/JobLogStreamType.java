package com.pcrm.backend.jobs.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

public enum JobLogStreamType {
    STDOUT("stdout"),
    STDERR("stderr");

    private final String nomadValue;

    JobLogStreamType(String nomadValue) {
        this.nomadValue = nomadValue;
    }

    public String nomadValue() {
        return nomadValue;
    }

    public static JobLogStreamType from(String rawValue) {
        if (rawValue == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Stream type is required. Allowed values: stdout, stderr."
            );
        }

        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "stdout" -> STDOUT;
            case "stderr" -> STDERR;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid stream type. Allowed values: stdout, stderr."
            );
        };
    }
}
