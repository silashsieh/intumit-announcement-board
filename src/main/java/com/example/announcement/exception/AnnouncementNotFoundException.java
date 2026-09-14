package com.example.announcement.exception;

public class AnnouncementNotFoundException extends RuntimeException {

	public AnnouncementNotFoundException() {
		super("Announcement not found");
	}
}
