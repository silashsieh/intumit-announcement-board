package com.example.announcement.controller;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.announcement.dto.AnnouncementRequest;
import com.example.announcement.dto.AnnouncementResponse;
import com.example.announcement.dto.PageResponse;
import com.example.announcement.exception.RequestValidationException;
import com.example.announcement.service.AnnouncementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

	private final AnnouncementService service;

	public AnnouncementController(AnnouncementService service) {
		this.service = service;
	}

	@GetMapping
	public PageResponse<AnnouncementResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		validatePage(page, size);
		return service.list(page, size);
	}

	@GetMapping("/{id}")
	public AnnouncementResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	public ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest request) {
		AnnouncementResponse created = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.getId())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public AnnouncementResponse update(
			@PathVariable Long id,
			@Valid @RequestBody AnnouncementRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

	private static void validatePage(int page, int size) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		if (page < 0) {
			fieldErrors.put("page", "Page must be 0 or greater");
		}
		if (size < 1 || size > 100) {
			fieldErrors.put("size", "Size must be between 1 and 100");
		}
		if (!fieldErrors.isEmpty()) {
			throw new RequestValidationException(fieldErrors);
		}
	}
}
