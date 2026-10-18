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
	void seedsTheDefaultGeneticAlgorithmParameters() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		var row = jdbc.queryForMap("SELECT populacao, geracoes, taxa_cruzamento, taxa_mutacao FROM parametros_ag");

		assertThat(row.get("populacao")).isEqualTo(100);
		assertThat(row.get("geracoes")).isEqualTo(200);
		assertThat(((Number) row.get("taxa_cruzamento")).doubleValue()).isEqualTo(0.8);
		assertThat(((Number) row.get("taxa_mutacao")).doubleValue()).isEqualTo(0.05);
	}

	@Test
	void refusesParametersOutsideTheAllowedRangesAtTheDatabaseToo() {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);

		org.assertj.core.api.Assertions.assertThatThrownBy(
			() -> jdbc.update("UPDATE parametros_ag SET taxa_mutacao = 2 WHERE id = 1"))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	void startsFromVersionOne() {
		assertThat(flyway.info().all()[0].getVersion()).isEqualTo(MigrationVersion.fromVersion("1"));
	}
}
