package com.example.announcement;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AnnouncementBoardApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoadsAgainstMysql() throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.isValid(5)).isTrue();
			assertThat(connection.getMetaData().getDatabaseProductName()).containsIgnoringCase("MySQL");
		}
	}
}
