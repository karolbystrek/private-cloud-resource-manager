package com.pcrm.broker.jobs.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = com.pcrm.broker.jobs.validation.EnvironmentVariablesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEnvironmentVariables {

    String message() default "envVars.INVALID";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
