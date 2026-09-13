package com.example.announcement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.announcement.domain.Announcement;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
}
