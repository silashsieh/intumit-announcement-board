package com.example.announcement.dto;

import java.time.LocalDate;

public class AnnouncementResponse {

	private Long id;
	private String title;
	private String publisher;
	private LocalDate publishDate;
	private LocalDate deadlineDate;
	private String content;

	public AnnouncementResponse() {
	}

	public AnnouncementResponse(
			Long id,
			String title,
			String publisher,
			LocalDate publishDate,
			LocalDate deadlineDate,
			String content) {
		this.id = id;
		this.title = title;
		this.publisher = publisher;
		this.publishDate = publishDate;
		this.deadlineDate = deadlineDate;
		this.content = content;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getPublisher() {
		return publisher;
	}

	public LocalDate getPublishDate() {
		return publishDate;
	}

	public LocalDate getDeadlineDate() {
		return deadlineDate;
	}

	public String getContent() {
		return content;
	}
}
