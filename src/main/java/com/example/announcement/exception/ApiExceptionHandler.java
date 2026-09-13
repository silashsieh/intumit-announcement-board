package com.example.announcement.exception;

import java.time.DateTimeException;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(AnnouncementNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound() {
		return error(HttpStatus.NOT_FOUND, "Announcement not found", Map.of());
	}

	@ExceptionHandler(RequestValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleRequestValidation(RequestValidationException ex) {
		return error(HttpStatus.BAD_REQUEST, "Validation failed", ex.getFieldErrors());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return error(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
		if (isDateFormatProblem(ex)) {
			return error(HttpStatus.BAD_REQUEST, "Invalid date format", Map.of());
		}
		return error(HttpStatus.BAD_REQUEST, "Malformed JSON request", Map.of());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		String field = ex.getName() == null ? "value" : ex.getName();
		return error(HttpStatus.BAD_REQUEST, "Invalid request parameter", Map.of(field, "Invalid value"));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataIntegrity() {
		log.warn("Database constraint violation while processing an announcement request");
		return error(HttpStatus.BAD_REQUEST, "The announcement could not be saved", Map.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
		log.error("Unexpected error while processing an announcement request", ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", Map.of());
	}

	private static ResponseEntity<ApiErrorResponse> error(
			HttpStatus status,
			String message,
			Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(message, fieldErrors));
	}

	private static boolean isDateFormatProblem(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof DateTimeException) {
				return true;
			}
			if (current instanceof InvalidFormatException invalidFormat) {
				Class<?> targetType = invalidFormat.getTargetType();
				if (targetType != null && Temporal.class.isAssignableFrom(targetType)) {
					return true;
				}
			}
			current = current.getCause();
		}
		return false;
	}
}
