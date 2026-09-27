package br.com.greenvrp.config;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DatabaseConfigTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void applicationContextLoadsWithDataSourceConfigured() throws Exception {
		assertThat(dataSource).isNotNull();
		assertThat(dataSource.getConnection().isValid(2)).isTrue();
	}

}
