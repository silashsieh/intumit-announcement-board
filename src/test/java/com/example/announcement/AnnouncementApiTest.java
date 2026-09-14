package com.example.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.announcement.domain.Announcement;
import com.example.announcement.repository.AnnouncementRepository;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnnouncementApiTest {

	private static final String API = "/api/announcements";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AnnouncementRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private DataSource dataSource;

	@AfterAll
	static void mysqlHasNoLeftoverAnnouncements(@Autowired DataSource dataSource) throws Exception {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM announcements");
				ResultSet resultSet = statement.executeQuery()) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getLong(1)).isZero();
		}
	}

	@Test
	void usesInstalledMysql() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("MySQL");
			assertThat(connection.getMetaData().getURL()).contains("mysql");
			assertThat(connection.getMetaData().getURL()).doesNotContain("h2", "hsqldb", "derby");
		}
	}

	@Test
	void unknownPathRemains404() throws Exception {
		mockMvc.perform(get("/this-path-does-not-exist"))
				.andExpect(status().isNotFound());
	}

	@Test
	void listDefaultsToEmptyPageMetadata() throws Exception {
		mockMvc.perform(get(API))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.totalItems").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void listUsesFixedPublishDateThenIdOrdering() throws Exception {
		Long olderId = createAndReturnId("Older", "2026-09-10", "2026-09-20");
		Long earlierSameDayId = createAndReturnId("Earlier same day", "2026-09-12", "2026-09-20");
		Long laterSameDayId = createAndReturnId("Later same day", "2026-09-12", "2026-09-20");

		mockMvc.perform(get(API))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(3))
				.andExpect(jsonPath("$.items[0].id").value(laterSameDayId))
				.andExpect(jsonPath("$.items[0].title").value("Later same day"))
				.andExpect(jsonPath("$.items[1].id").value(earlierSameDayId))
				.andExpect(jsonPath("$.items[2].id").value(olderId));
	}

	@Test
	void listHonorsCustomPageAndSize() throws Exception {
		createAndReturnId("First", "2026-09-10", "2026-09-20");
		createAndReturnId("Second", "2026-09-11", "2026-09-20");
		createAndReturnId("Third", "2026-09-12", "2026-09-20");

		mockMvc.perform(get(API).param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalItems").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].title").value("First"));
	}

	@Test
	void listRejectsNegativePage() throws Exception {
		mockMvc.perform(get(API).param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.page").value("Page must be 0 or greater"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void listRejectsZeroAndOversizedSize() throws Exception {
		mockMvc.perform(get(API).param("size", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.size").value("Size must be between 1 and 100"));

		mockMvc.perform(get(API).param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.size").value("Size must be between 1 and 100"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void getReturnsCreatedAnnouncement() throws Exception {
		Long id = createAndReturnId("System maintenance", "2026-09-11", "2026-09-18");

		mockMvc.perform(get(API + "/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.title").value("System maintenance"))
				.andExpect(jsonPath("$.publisher").value("Administrator"))
				.andExpect(jsonPath("$.publishDate").value("2026-09-11"))
				.andExpect(jsonPath("$.deadlineDate").value("2026-09-18"))
				.andExpect(jsonPath("$.content").value("The service will be unavailable."))
				.andExpect(jsonPath("$.createdAt").doesNotExist())
				.andExpect(jsonPath("$.updatedAt").doesNotExist());
	}

	@Test
	void getMissingAnnouncementReturns404() throws Exception {
		mockMvc.perform(get(API + "/" + Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Announcement not found"))
				.andExpect(jsonPath("$.fieldErrors").isMap())
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void createReturns201WithGeneratedIdAndLocation() throws Exception {
		MvcResult result = mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("System maintenance", "Administrator", "2026-09-11", "2026-09-18",
								"The service will be unavailable.")))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.containsString(API + "/")))
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("System maintenance"))
				.andExpect(jsonPath("$.publisher").value("Administrator"))
				.andExpect(jsonPath("$.publishDate").value("2026-09-11"))
				.andExpect(jsonPath("$.deadlineDate").value("2026-09-18"))
				.andExpect(jsonPath("$.content").value("The service will be unavailable."))
				.andExpect(jsonPath("$.createdAt").doesNotExist())
				.andExpect(jsonPath("$.updatedAt").doesNotExist())
				.andReturn();

		Long id = readId(result);
		assertThat(id).isPositive();
		assertThat(repository.findById(id)).isPresent();
	}

	@Test
	void createAcceptsEqualPublishAndDeadlineDates() throws Exception {
		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("Same-day notice", "Administrator", "2026-09-11", "2026-09-11",
								"Deadline matches publish date.")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.publishDate").value("2026-09-11"))
				.andExpect(jsonPath("$.deadlineDate").value("2026-09-11"));
	}

	@Test
	void createRejectsBlankTitlePublisherAndContent() throws Exception {
		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("  ", "  ", "2026-09-11", "2026-09-18", "  ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
				.andExpect(jsonPath("$.fieldErrors.publisher").value("Publisher is required"))
				.andExpect(jsonPath("$.fieldErrors.content").value("Content is required"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void createRejectsMissingDates() throws Exception {
		String body = """
				{
				  "title": "System maintenance",
				  "publisher": "Administrator",
				  "content": "The service will be unavailable."
				}
				""";

		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.publishDate").value("Publish date is required"))
				.andExpect(jsonPath("$.fieldErrors.deadlineDate").value("Deadline date is required"));
	}

	@Test
	void createRejectsTitleAndPublisherLengthLimits() throws Exception {
		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("a".repeat(201), "Administrator", "2026-09-11", "2026-09-18",
								"The service will be unavailable.")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.title").value("Title must not exceed 200 characters"));

		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("System maintenance", "b".repeat(101), "2026-09-11", "2026-09-18",
								"The service will be unavailable.")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.publisher").value("Publisher must not exceed 100 characters"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void createRejectsDeadlineBeforePublishDate() throws Exception {
		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("System maintenance", "Administrator", "2026-09-18", "2026-09-11",
								"The service will be unavailable.")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.deadlineDate")
						.value("Deadline date must be on or after the publish date"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void createRejectsMalformedJsonAndInvalidDate() throws Exception {
		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed JSON request"))
				.andExpect(jsonPath("$.fieldErrors").isMap())
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));

		mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("System maintenance", "Administrator", "2026-13-40", "2026-09-18",
								"The service will be unavailable.")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid date format"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void updateReplacesEditableFieldsAndPreservesIdAndCreatedAt() throws Exception {
		Long id = createAndReturnId("Original title", "2026-09-11", "2026-09-18");
		entityManager.flush();
		entityManager.clear();

		Announcement created = repository.findById(id).orElseThrow();
		LocalDateTime createdAt = created.getCreatedAt();
		assertThat(createdAt).isNotNull();

		Thread.sleep(15);

		mockMvc.perform(put(API + "/" + id)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("Updated title", "Editor", "2026-09-12", "2026-09-19",
								"Updated content")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.title").value("Updated title"))
				.andExpect(jsonPath("$.publisher").value("Editor"))
				.andExpect(jsonPath("$.publishDate").value("2026-09-12"))
				.andExpect(jsonPath("$.deadlineDate").value("2026-09-19"))
				.andExpect(jsonPath("$.content").value("Updated content"))
				.andExpect(jsonPath("$.createdAt").doesNotExist());

		entityManager.flush();
		entityManager.clear();
		Announcement updated = repository.findById(id).orElseThrow();
		assertThat(updated.getId()).isEqualTo(id);
		assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
		assertThat(updated.getUpdatedAt()).isAfter(createdAt);
		assertThat(updated.getTitle()).isEqualTo("Updated title");
	}

	@Test
	void updateMissingAnnouncementReturns404() throws Exception {
		mockMvc.perform(put(API + "/" + Long.MAX_VALUE)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson("Updated title", "Editor", "2026-09-12", "2026-09-19",
								"Updated content")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Announcement not found"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void deleteReturns204AndRemovesRow() throws Exception {
		Long id = createAndReturnId("To delete", "2026-09-11", "2026-09-18");
		assertThat(repository.existsById(id)).isTrue();

		mockMvc.perform(delete(API + "/" + id))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		assertThat(repository.existsById(id)).isFalse();
	}

	@Test
	void deleteMissingAnnouncementReturns404() throws Exception {
		mockMvc.perform(delete(API + "/" + Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Announcement not found"))
				.andExpect(result -> assertSafeErrorBody(result.getResponse().getContentAsString()));
	}

	@Test
	void createIsVisibleInsideTheTestTransaction() throws Exception {
		assertThat(repository.count()).isZero();
		createAndReturnId("Visible in transaction", "2026-09-11", "2026-09-18");
		assertThat(repository.count()).isEqualTo(1);
	}

	private Long createAndReturnId(String title, String publishDate, String deadlineDate) throws Exception {
		MvcResult result = mockMvc.perform(post(API)
						.contentType(MediaType.APPLICATION_JSON)
						.content(validJson(title, "Administrator", publishDate, deadlineDate,
								"The service will be unavailable.")))
				.andExpect(status().isCreated())
				.andReturn();
		return readId(result);
	}

	private static Long readId(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static String validJson(
			String title,
			String publisher,
			String publishDate,
			String deadlineDate,
			String content) {
		return """
				{
				  "title": %s,
				  "publisher": %s,
				  "publishDate": %s,
				  "deadlineDate": %s,
				  "content": %s
				}
				""".formatted(
				jsonValue(title),
				jsonValue(publisher),
				jsonValue(publishDate),
				jsonValue(deadlineDate),
				jsonValue(content));
	}

	private static String jsonValue(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static void assertSafeErrorBody(String body) {
		assertThat(body).contains("\"message\"");
		assertThat(body).contains("\"fieldErrors\"");
		assertThat(body).doesNotContain(
				"Exception",
				"stackTrace",
				"Caused by",
				"SQLException",
				"password",
				"jdbc:",
				"announcements",
				"at com.example");
	}
}
