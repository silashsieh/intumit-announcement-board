package com.example.announcement.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class RequestValidationException extends RuntimeException {

	private final Map<String, String> fieldErrors;

	public RequestValidationException(Map<String, String> fieldErrors) {
		super("Validation failed");
		this.fieldErrors = new LinkedHashMap<>(fieldErrors);
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
}
