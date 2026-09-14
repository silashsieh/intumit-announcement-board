package com.example.announcement.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@DeadlineOnOrAfterPublishDate
public class AnnouncementRequest {

	@NotBlank(message = "Title is required")
	@Size(max = 200, message = "Title must not exceed 200 characters")
	private String title;

	@NotBlank(message = "Publisher is required")
	@Size(max = 100, message = "Publisher must not exceed 100 characters")
	private String publisher;

	@NotNull(message = "Publish date is required")
	private LocalDate publishDate;

	@NotNull(message = "Deadline date is required")
	private LocalDate deadlineDate;

	@NotBlank(message = "Content is required")
	private String content;

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public LocalDate getPublishDate() {
		return publishDate;
	}

	public void setPublishDate(LocalDate publishDate) {
		this.publishDate = publishDate;
	}

	public LocalDate getDeadlineDate() {
		return deadlineDate;
	}

	public void setDeadlineDate(LocalDate deadlineDate) {
		this.deadlineDate = deadlineDate;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
