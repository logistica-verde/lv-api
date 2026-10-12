package br.com.greenvrp.migration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:lv-api-migration;MODE=MSSQLServer;DB_CLOSE_DELAY=-1",
	"spring.flyway.enabled=true",
	"spring.jpa.hibernate.ddl-auto=validate"
})
class SchemaMigrationTest {

	@Autowired
	private Flyway flyway;

	@Autowired
	private DataSource dataSource;

	@Test
	void appliesEveryMigrationAndTheEntitiesValidateAgainstTheResult() {
		assertThat(flyway.info().pending()).isEmpty();
		assertThat(flyway.info().current().getVersion()).isNotNull();
	}

	@Test
	void createsTheTablesTheApplicationUses() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		var tables = jdbc.queryForList(
			"SELECT LOWER(table_name) FROM information_schema.tables WHERE table_schema = 'DBO' OR table_schema = 'PUBLIC'",
			String.class);

		assertThat(tables).contains(
			"veiculos", "pontos_entrega", "malha_viaria", "operacoes",
			"operacao_pontos", "segmentos_rota", "registros_blockchain");
	}

	@Test
	void startsFromVersionOne() {
		assertThat(flyway.info().all()[0].getVersion()).isEqualTo(MigrationVersion.fromVersion("1"));
	}
}
