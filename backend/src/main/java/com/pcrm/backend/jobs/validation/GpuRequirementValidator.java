package com.pcrm.backend.jobs.validation;

import com.pcrm.backend.jobs.dto.GpuRequirement;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class GpuRequirementValidator implements ConstraintValidator<ValidGpuRequirement, GpuRequirement> {

    @Override
    public boolean isValid(GpuRequirement value, ConstraintValidatorContext context) {
        if (value == null || !Boolean.TRUE.equals(value.enabled())) {
            return true;
        }

        var valid = true;
        context.disableDefaultConstraintViolation();

        if (value.count() == null || value.count() < 1) {
            addViolation(context, "count", "GPU count must be at least 1 when GPU is enabled.");
            valid = false;
        }

        if (value.vendor() == null || !GpuRequirement.NVIDIA_VENDOR.equalsIgnoreCase(value.vendor().trim())) {
            addViolation(context, "vendor", "Only NVIDIA GPUs are supported.");
            valid = false;
        }

        if (value.model() == null || value.model().isBlank()) {
            addViolation(context, "model", "GPU model must be selected from available cluster GPUs.");
            valid = false;
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String property, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
