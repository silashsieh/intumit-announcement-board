package com.example.announcement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.example.announcement.domain.Announcement;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnnouncementRepositoryTest {

	@Autowired
	private AnnouncementRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private DataSource dataSource;

	@Test
	void usesInstalledMysql() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("MySQL");
			assertThat(connection.getMetaData().getURL()).contains("mysql");
			assertThat(connection.getMetaData().getURL()).doesNotContain("h2", "hsqldb", "derby");
		}
	}

	@Test
	void saveGeneratesIdAndMapsAllFields() {
		Announcement announcement = newAnnouncement(
				"System maintenance",
				"Administrator",
				LocalDate.of(2026, 9, 11),
				LocalDate.of(2026, 9, 18),
				"The service will be unavailable.");

		Announcement saved = repository.saveAndFlush(announcement);
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());

		Long id = saved.getId();
		entityManager.clear();

		Announcement loaded = repository.findById(id).orElseThrow();
		assertThat(loaded.getId()).isEqualTo(id);
		assertThat(loaded.getTitle()).isEqualTo("System maintenance");
		assertThat(loaded.getPublisher()).isEqualTo("Administrator");
		assertThat(loaded.getPublishDate()).isEqualTo(LocalDate.of(2026, 9, 11));
		assertThat(loaded.getDeadlineDate()).isEqualTo(LocalDate.of(2026, 9, 18));
		assertThat(loaded.getContent()).isEqualTo("The service will be unavailable.");
		assertThat(loaded.getCreatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(loaded.getUpdatedAt()).isEqualTo(saved.getUpdatedAt());
	}

	@Test
	void updatedAtAdvancesOnUpdateWhileCreatedAtStays() throws Exception {
		Announcement saved = repository.saveAndFlush(newAnnouncement(
				"Original title",
				"Administrator",
				LocalDate.of(2026, 9, 11),
				LocalDate.of(2026, 9, 18),
				"Original content"));
		entityManager.clear();

		Announcement loaded = repository.findById(saved.getId()).orElseThrow();
		LocalDateTime createdAt = loaded.getCreatedAt();
		LocalDateTime updatedAt = loaded.getUpdatedAt();
		assertThat(createdAt).isNotNull();
		assertThat(updatedAt).isNotNull();

		Thread.sleep(15);
		loaded.setTitle("Updated title");
		repository.saveAndFlush(loaded);
		entityManager.clear();

		Announcement reloaded = repository.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.getTitle()).isEqualTo("Updated title");
		assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
		assertThat(reloaded.getUpdatedAt()).isAfter(updatedAt);
	}

	@Test
	void pageableSortsByPublishDateDescThenIdDesc() {
		Announcement older = repository.saveAndFlush(newAnnouncement(
				"Older",
				"Administrator",
				LocalDate.of(2026, 9, 10),
				LocalDate.of(2026, 9, 20),
				"Older notice"));
		Announcement earlierSameDay = repository.saveAndFlush(newAnnouncement(
				"Earlier same day",
				"Administrator",
				LocalDate.of(2026, 9, 12),
				LocalDate.of(2026, 9, 20),
				"First same-day notice"));
		Announcement laterSameDay = repository.saveAndFlush(newAnnouncement(
				"Later same day",
				"Administrator",
				LocalDate.of(2026, 9, 12),
				LocalDate.of(2026, 9, 20),
				"Second same-day notice"));
		entityManager.clear();

		Page<Announcement> page = repository.findAll(PageRequest.of(
				0,
				10,
				Sort.by(Sort.Order.desc("publishDate"), Sort.Order.desc("id"))));

		assertThat(page.getTotalElements()).isEqualTo(3);
		List<Long> ids = page.getContent().stream().map(Announcement::getId).toList();
		assertThat(ids).containsExactly(laterSameDay.getId(), earlierSameDay.getId(), older.getId());
	}

	private static Announcement newAnnouncement(
			String title,
			String publisher,
			LocalDate publishDate,
			LocalDate deadlineDate,
			String content) {
		Announcement announcement = new Announcement();
		announcement.setTitle(title);
		announcement.setPublisher(publisher);
		announcement.setPublishDate(publishDate);
		announcement.setDeadlineDate(deadlineDate);
		announcement.setContent(content);
		return announcement;
	}
}
