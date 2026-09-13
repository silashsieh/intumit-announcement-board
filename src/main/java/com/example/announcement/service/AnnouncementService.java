package com.example.announcement.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.announcement.domain.Announcement;
import com.example.announcement.dto.AnnouncementRequest;
import com.example.announcement.dto.AnnouncementResponse;
import com.example.announcement.dto.PageResponse;
import com.example.announcement.exception.AnnouncementNotFoundException;
import com.example.announcement.repository.AnnouncementRepository;

@Service
public class AnnouncementService {

	private static final Sort DEFAULT_SORT = Sort.by(
			Sort.Order.desc("publishDate"),
			Sort.Order.desc("id"));

	private final AnnouncementRepository repository;

	public AnnouncementService(AnnouncementRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public PageResponse<AnnouncementResponse> list(int page, int size) {
		Page<Announcement> result = repository.findAll(PageRequest.of(page, size, DEFAULT_SORT));
		return new PageResponse<>(
				result.getContent().stream().map(AnnouncementService::toResponse).toList(),
				result.getNumber(),
				result.getSize(),
				result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public AnnouncementResponse get(Long id) {
		return toResponse(findOrThrow(id));
	}

	@Transactional
	public AnnouncementResponse create(AnnouncementRequest request) {
		Announcement announcement = new Announcement();
		apply(announcement, request);
		return toResponse(repository.save(announcement));
	}

	@Transactional
	public AnnouncementResponse update(Long id, AnnouncementRequest request) {
		Announcement announcement = findOrThrow(id);
		apply(announcement, request);
		return toResponse(repository.save(announcement));
	}

	@Transactional
	public void delete(Long id) {
		repository.delete(findOrThrow(id));
	}

	private Announcement findOrThrow(Long id) {
		return repository.findById(id).orElseThrow(AnnouncementNotFoundException::new);
	}

	private static void apply(Announcement announcement, AnnouncementRequest request) {
		announcement.setTitle(request.getTitle());
		announcement.setPublisher(request.getPublisher());
		announcement.setPublishDate(request.getPublishDate());
		announcement.setDeadlineDate(request.getDeadlineDate());
		announcement.setContent(request.getContent());
	}

	private static AnnouncementResponse toResponse(Announcement announcement) {
		return new AnnouncementResponse(
				announcement.getId(),
				announcement.getTitle(),
				announcement.getPublisher(),
				announcement.getPublishDate(),
				announcement.getDeadlineDate(),
				announcement.getContent());
	}
}
