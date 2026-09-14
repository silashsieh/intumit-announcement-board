package com.example.announcement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AnnouncementUiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rootServesAnnouncementBoardHtml() throws Exception {
		mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("index.html"));

		mockMvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("<h1")))
				.andExpect(content().string(containsString("Announcement Board")))
				.andExpect(content().string(containsString("id=\"announcement-table\"")))
				.andExpect(content().string(containsString("id=\"announcement-table-body\"")))
				.andExpect(content().string(containsString("id=\"announcement-modal\"")))
				.andExpect(content().string(containsString("id=\"delete-modal\"")))
				.andExpect(content().string(containsString("id=\"announcement-loading\"")))
				.andExpect(content().string(containsString("id=\"announcement-empty\"")))
				.andExpect(content().string(containsString("id=\"announcement-feedback\"")))
				.andExpect(content().string(containsString("id=\"announcement-pagination\"")))
				.andExpect(content().string(containsString("href=\"css/app.css\"")))
				.andExpect(content().string(containsString("src=\"js/app.js\"")))
				.andExpect(content().string(not(containsString("window.confirm"))))
				.andExpect(content().string(not(containsString("/api/announcements"))));
	}

	@Test
	void appCssIsServed() throws Exception {
		mockMvc.perform(get("/css/app.css"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/css"));
	}

	@Test
	void appJsIsServed() throws Exception {
		mockMvc.perform(get("/js/app.js"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("preventDefault")))
				.andExpect(content().string(not(containsString("/api/announcements"))));
	}

	@Test
	void unknownPathRemains404() throws Exception {
		mockMvc.perform(get("/this-path-does-not-exist"))
				.andExpect(status().isNotFound());
	}
}
