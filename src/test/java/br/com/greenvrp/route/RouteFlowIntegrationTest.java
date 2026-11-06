package br.com.greenvrp.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.blockchain.InMemoryLedger;
import br.com.greenvrp.geo.RoadSegment;
import br.com.greenvrp.geo.SpatialRepository;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class RouteFlowIntegrationTest {

	@TestConfiguration
	static class LedgerTestConfig {

		@Bean
		@Primary
		InMemoryLedger inMemoryLedger() {
			return new InMemoryLedger();
		}

	}

	private record CompletedRoute(long routeId, List<Long> pointIds, String body) {
	}

	private static final AtomicReference<String> RECEIVED_BODY = new AtomicReference<>();
	private static HttpServer fakeOptimizer;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private InMemoryLedger ledger;

	@MockitoBean
	private SpatialRepository spatialRepository;

	@BeforeAll
	static void startFakeOptimizer() throws Exception {
		fakeOptimizer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		fakeOptimizer.createContext("/optimize", exchange -> {
			RECEIVED_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] body = """
				{"sequence":[2,0,1],"total_emission_kg":5.5,"total_distance_meters":2000.0,
				 "segment_emissions":[{"from_index":2,"to_index":0,"emission_kg":2.0},
				                      {"from_index":0,"to_index":1,"emission_kg":3.5}]}
				""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		fakeOptimizer.createContext("/optimize/stream", exchange -> {
			RECEIVED_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] body = """
				{"type":"progress","generation":0,"generations":10}
				{"type":"progress","generation":10,"generations":10}
				{"type":"result","result":{"sequence":[2,0,1],"total_emission_kg":5.5,"total_distance_meters":2000.0,\
				"segment_emissions":[{"from_index":2,"to_index":0,"emission_kg":2.0},\
				{"from_index":0,"to_index":1,"emission_kg":3.5}]}}
				""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_NDJSON_VALUE);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		fakeOptimizer.start();
	}

	@AfterAll
	static void stopFakeOptimizer() {
		fakeOptimizer.stop(0);
	}

	@DynamicPropertySource
	static void optimizerUrl(DynamicPropertyRegistry registry) {
		registry.add("optimizer.url", () -> "http://localhost:" + fakeOptimizer.getAddress().getPort());
	}

	@BeforeEach
	void resetFixtures() {
		ledger.clear();
		GeometryFactory factory = new GeometryFactory();
		given(spatialRepository.findNearestSegment(anyString())).willReturn(Optional.of(new RoadSegment(
			factory.createLineString(new Coordinate[] {new Coordinate(0, 0), new Coordinate(0.001, 0.001)}),
			40.0, 2.0, 120.0)));
	}

	private long createAndGetId(String url, String json) throws Exception {
		String body = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().is2xxSuccessful())
			.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private CompletedRoute calculateRoute() throws Exception {
		long vehicleId = createAndGetId("/api/vehicles",
			"{\"placa\":\"FLW1A23\",\"massaKg\":7500.0,\"tipoCombustivel\":\"diesel\"}");
		List<Long> pointIds = new ArrayList<>();
		for (int index = 0; index < 3; index++) {
			pointIds.add(createAndGetId("/api/delivery-points",
				"{\"nome\":\"P" + index + "\",\"latitude\":" + (-23.55 - index * 0.01) + ",\"longitude\":-46.63}"));
		}

		String accepted = mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehicleId\":%d,\"deliveryPointIds\":%s,\"criterion\":\"EMISSION\"}"
					.formatted(vehicleId, pointIds)))
			.andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();
		long routeId = ((Number) JsonPath.read(accepted, "$.id")).longValue();

		String result = null;
		for (int attempt = 0; attempt < 100; attempt++) {
			result = mockMvc.perform(get("/api/routes/" + routeId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
			if (!"PENDING".equals(JsonPath.read(result, "$.status"))) {
				break;
			}
			Thread.sleep(100);
		}
		return new CompletedRoute(routeId, pointIds, result);
	}

	private String integrityStatus(long routeId) throws Exception {
		String body = mockMvc.perform(get("/api/routes/" + routeId + "/integrity"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.status");
	}

	private String awaitAnchoring(long routeId) throws Exception {
		String integrity = integrityStatus(routeId);
		for (int attempt = 0; attempt < 100 && "NOT_ANCHORED".equals(integrity); attempt++) {
			Thread.sleep(100);
			integrity = integrityStatus(routeId);
		}
		return integrity;
	}

	@Test
	void calculatesRouteAsynchronouslyAndExposesResultByPolling() throws Exception {
		CompletedRoute route = calculateRoute();
		String result = route.body();
		List<Long> pointIds = route.pointIds();

		assertThat((String) JsonPath.read(result, "$.status")).isEqualTo("COMPLETED");
		assertThat(((Number) JsonPath.read(result, "$.totalEmissionKg")).doubleValue()).isEqualTo(5.5);
		List<Integer> visitedPointIds = JsonPath.read(result, "$.points[*].pointId");
		assertThat(visitedPointIds.stream().map(Integer::longValue).toList())
			.containsExactly(pointIds.get(2), pointIds.get(0), pointIds.get(1));
		List<Integer> segmentOrigins = JsonPath.read(result, "$.segments[*].fromPointId");
		assertThat(segmentOrigins.stream().map(Integer::longValue).toList())
			.containsExactly(pointIds.get(2), pointIds.get(0));

		assertThat(RECEIVED_BODY.get())
			.contains("\"criterion\":\"emission\"")
			.contains("\"vehicle_mass_kg\":7500.0")
			.contains("\"point_count\":3");
	}

	@Test
	void anchorsTheCompletedRouteAndReportsItAsValid() throws Exception {
		CompletedRoute route = calculateRoute();

		assertThat(awaitAnchoring(route.routeId())).isEqualTo("VALID");

		String report = mockMvc.perform(get("/api/routes/" + route.routeId() + "/integrity"))
			.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(report, "$.databaseHash")).hasSize(64)
			.isEqualTo(JsonPath.read(report, "$.ledgerHash"));
		assertThat((String) JsonPath.read(report, "$.txHash")).startsWith("0x");
	}

	@Test
	void detectsEveryKindOfAlterationMadeDirectlyInTheDatabase() throws Exception {
		CompletedRoute route = calculateRoute();
		assertThat(awaitAnchoring(route.routeId())).isEqualTo("VALID");
		long id = route.routeId();

		List<String[]> alterations = List.of(
			new String[] {"update operacoes set emissao_total_kg = emissao_total_kg + 1 where id = ?",
				"update operacoes set emissao_total_kg = emissao_total_kg - 1 where id = ?"},
			new String[] {"update operacoes set criterio = 'DISTANCE' where id = ?",
				"update operacoes set criterio = 'EMISSION' where id = ?"},
			new String[] {"update operacoes set data_hora = data_hora + INTERVAL '1' SECOND where id = ?",
				"update operacoes set data_hora = data_hora - INTERVAL '1' SECOND where id = ?"},
			new String[] {"update segmentos_rota set emissao_kg_co2 = emissao_kg_co2 + 0.5 where id_operacao = ?",
				"update segmentos_rota set emissao_kg_co2 = emissao_kg_co2 - 0.5 where id_operacao = ?"},
			new String[] {"update segmentos_rota set distancia_metros = distancia_metros + 10 where id_operacao = ?",
				"update segmentos_rota set distancia_metros = distancia_metros - 10 where id_operacao = ?"},
			new String[] {"update segmentos_rota set velocidade_media = velocidade_media + 5 where id_operacao = ?",
				"update segmentos_rota set velocidade_media = velocidade_media - 5 where id_operacao = ?"},
			new String[] {"update segmentos_rota set declividade = declividade + 1 where id_operacao = ?",
				"update segmentos_rota set declividade = declividade - 1 where id_operacao = ?"},
			new String[] {"update segmentos_rota set ordem = 3 - ordem where id_operacao = ?",
				"update segmentos_rota set ordem = 3 - ordem where id_operacao = ?"},
			new String[] {"update segmentos_rota set id_ponto_origem = id_ponto_destino, id_ponto_destino = id_ponto_origem where id_operacao = ?",
				"update segmentos_rota set id_ponto_origem = id_ponto_destino, id_ponto_destino = id_ponto_origem where id_operacao = ?"},
			new String[] {"update operacao_pontos set ordem_visita = 100 - ordem_visita where id_operacao = ?",
				"update operacao_pontos set ordem_visita = 100 - ordem_visita where id_operacao = ?"});
		assertThat(alterations).hasSize(10);

		for (String[] alteration : alterations) {
			jdbcTemplate.update(alteration[0], id);
			assertThat(integrityStatus(id)).as("after: %s", alteration[0]).isEqualTo("TAMPERED");

			jdbcTemplate.update(alteration[1], id);
			assertThat(integrityStatus(id)).as("after reverting: %s", alteration[0]).isEqualTo("VALID");
		}
	}

}
