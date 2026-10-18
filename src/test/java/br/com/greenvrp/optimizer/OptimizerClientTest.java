package br.com.greenvrp.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.greenvrp.geo.PairSegment;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class OptimizerClientTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private OptimizerClient clientFor(HttpServer fakeOptimizer, Duration timeout) {
		this.server = fakeOptimizer;
		fakeOptimizer.start();
		String baseUrl = "http://localhost:" + fakeOptimizer.getAddress().getPort();
		OptimizerProperties properties = new OptimizerProperties(baseUrl, timeout);
		return new OptimizerClient(WebClient.builder().baseUrl(baseUrl).build(), properties);
	}

	private HttpServer fakeOptimizer(int status, String body, AtomicReference<String> capturedBody) throws IOException {
		HttpServer fake = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		fake.createContext("/optimize", exchange -> {
			if (capturedBody != null) {
				capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		return fake;
	}

	private OptimizeRequest sampleRequest() {
		return OptimizeRequest.of(
			OptimizationCriterion.EMISSION,
			7500.0,
			2,
			List.of(new PairSegment(0, 1, 1200.0, 35.0, 1.5)));
	}

	@Test
	void postsSnakeCaseRequestAndParsesResponse() throws Exception {
		AtomicReference<String> captured = new AtomicReference<>();
		String responseJson = """
			{"sequence":[0,1],"total_emission_kg":3.2,"total_distance_meters":1200.0,
			 "segment_emissions":[{"from_index":0,"to_index":1,"emission_kg":3.2}]}
			""";
		OptimizerClient client = clientFor(fakeOptimizer(200, responseJson, captured), Duration.ofSeconds(5));

		OptimizeResponse response = client.optimize(sampleRequest()).get(5, TimeUnit.SECONDS);

		assertThat(captured.get())
			.contains("\"criterion\":\"emission\"")
			.contains("\"vehicle_mass_kg\":7500.0")
			.contains("\"point_count\":2")
			.contains("\"distance_meters\":1200.0")
			.contains("\"average_speed_kmh\":35.0")
			.contains("\"slope_percent\":1.5");
		assertThat(response.sequence()).containsExactly(0, 1);
		assertThat(response.totalEmissionKg()).isEqualTo(3.2);
		assertThat(response.totalDistanceMeters()).isEqualTo(1200.0);
		assertThat(response.segmentEmissions()).singleElement().satisfies(segment -> {
			assertThat(segment.fromIndex()).isZero();
			assertThat(segment.toIndex()).isEqualTo(1);
			assertThat(segment.emissionKg()).isEqualTo(3.2);
		});
	}

	@Test
	void sendsTheGeneticAlgorithmParametersInSnakeCaseWhenGiven() throws Exception {
		AtomicReference<String> captured = new AtomicReference<>();
		String responseJson = """
			{"sequence":[0,1],"total_emission_kg":3.2,"total_distance_meters":1200.0,"segment_emissions":[]}
			""";
		OptimizerClient client = clientFor(fakeOptimizer(200, responseJson, captured), Duration.ofSeconds(5));
		OptimizeRequest request = OptimizeRequest.of(
			OptimizationCriterion.EMISSION, 7500.0, 2,
			List.of(new PairSegment(0, 1, 1200.0, 35.0, 1.5)),
			new OptimizeRequest.GaParams(250, 400, 0.9, 0.1));

		client.optimize(request).get(5, TimeUnit.SECONDS);

		assertThat(captured.get())
			.contains("\"ga_params\":{")
			.contains("\"population_size\":250")
			.contains("\"generations\":400")
			.contains("\"crossover_rate\":0.9")
			.contains("\"mutation_rate\":0.1");
	}

	@Test
	void leavesOutTheParametersWhenNoneAreGiven() throws Exception {
		AtomicReference<String> captured = new AtomicReference<>();
		String responseJson = """
			{"sequence":[0,1],"total_emission_kg":3.2,"total_distance_meters":1200.0,"segment_emissions":[]}
			""";
		OptimizerClient client = clientFor(fakeOptimizer(200, responseJson, captured), Duration.ofSeconds(5));

		client.optimize(sampleRequest()).get(5, TimeUnit.SECONDS);

		assertThat(captured.get()).doesNotContain("ga_params");
	}

	@Test
	void mapsHttpErrorToOptimizerException() throws Exception {
		OptimizerClient client = clientFor(fakeOptimizer(500, "{\"detail\":\"boom\"}", null), Duration.ofSeconds(5));

		assertThatThrownBy(() -> client.optimize(sampleRequest()).get(5, TimeUnit.SECONDS))
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(OptimizerException.class);
	}

	@Test
	void mapsSlowResponseToOptimizerExceptionOnTimeout() throws Exception {
		HttpServer slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		slow.createContext("/optimize", exchange -> {
			try {
				Thread.sleep(2000);
			}
			catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			exchange.close();
		});
		OptimizerClient client = clientFor(slow, Duration.ofMillis(300));

		assertThatThrownBy(() -> client.optimize(sampleRequest()).get(5, TimeUnit.SECONDS))
			.isInstanceOf(ExecutionException.class)
			.hasCauseInstanceOf(OptimizerException.class);
	}

}
