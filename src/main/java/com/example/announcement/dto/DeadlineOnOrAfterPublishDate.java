package com.example.announcement.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DeadlineOnOrAfterPublishDateValidator.class)
public @interface DeadlineOnOrAfterPublishDate {

	String message() default "Deadline date must be on or after the publish date";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
