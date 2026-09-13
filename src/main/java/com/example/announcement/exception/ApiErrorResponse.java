package com.example.announcement.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiErrorResponse {

	private final String message;
	private final Map<String, String> fieldErrors;

	public ApiErrorResponse(String message, Map<String, String> fieldErrors) {
		this.message = message;
		this.fieldErrors = fieldErrors == null ? Map.of() : new LinkedHashMap<>(fieldErrors);
	}

	public static ApiErrorResponse of(String message) {
		return new ApiErrorResponse(message, Map.of());
	}

	public String getMessage() {
		return message;
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
}
