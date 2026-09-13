package com.example.announcement.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DeadlineOnOrAfterPublishDateValidator
		implements ConstraintValidator<DeadlineOnOrAfterPublishDate, AnnouncementRequest> {

	@Override
	public boolean isValid(AnnouncementRequest request, ConstraintValidatorContext context) {
		if (request == null || request.getPublishDate() == null || request.getDeadlineDate() == null) {
			return true;
		}
		if (!request.getDeadlineDate().isBefore(request.getPublishDate())) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode("deadlineDate")
				.addConstraintViolation();
		return false;
	}
}
