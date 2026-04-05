package com.pcrm.broker.jobs.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class EnvironmentVariablesValidator implements ConstraintValidator<ValidEnvironmentVariables, Map<String, String>> {

    private static final int MAX_ENV_VARS = 50;
    private static final int MAX_KEY_LENGTH = 255;
    private static final int MAX_VALUE_LENGTH = 4096;
    private static final String RESERVED_JOB_ID_KEY = "JOB_ID";
    private static final String RESERVED_NOMAD_PREFIX = "NOMAD_";
    private static final Pattern ENV_KEY_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Set<String> ERROR_CODES = Set.of(
            "envVars.MAX_ENTRIES_EXCEEDED",
            "envVars.INVALID_KEY",
            "envVars.RESERVED_KEY",
            "envVars.KEY_TOO_LONG",
            "envVars.VALUE_TOO_LONG"
    );

    @Override
    public boolean isValid(Map<String, String> value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean isValid = true;
        Set<String> emittedErrors = new java.util.HashSet<>();

        if (value.size() > MAX_ENV_VARS) {
            isValid = false;
            emitError(context, emittedErrors, "envVars.MAX_ENTRIES_EXCEEDED");
        }

        for (Map.Entry<String, String> entry : value.entrySet()) {
            String key = entry.getKey();
            String envValue = entry.getValue();

            if (key == null || !ENV_KEY_PATTERN.matcher(key).matches()) {
                isValid = false;
                emitError(context, emittedErrors, "envVars.INVALID_KEY");
            }

            if (key != null && key.length() > MAX_KEY_LENGTH) {
                isValid = false;
                emitError(context, emittedErrors, "envVars.KEY_TOO_LONG");
            }

            if (key != null && (key.equals(RESERVED_JOB_ID_KEY) || key.startsWith(RESERVED_NOMAD_PREFIX))) {
                isValid = false;
                emitError(context, emittedErrors, "envVars.RESERVED_KEY");
            }

            if (envValue != null && envValue.length() > MAX_VALUE_LENGTH) {
                isValid = false;
                emitError(context, emittedErrors, "envVars.VALUE_TOO_LONG");
            }
        }

        return isValid;
    }

    private void emitError(ConstraintValidatorContext context, Set<String> emittedErrors, String errorCode) {
        if (!ERROR_CODES.contains(errorCode) || emittedErrors.contains(errorCode)) {
            return;
        }
        emittedErrors.add(errorCode);
        context.buildConstraintViolationWithTemplate(errorCode)
                .addConstraintViolation();
    }
}
