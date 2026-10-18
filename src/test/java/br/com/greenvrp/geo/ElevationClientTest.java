package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class ElevationClientTest {

	private HttpServer server;
	private final List<String> locationsSeen = new CopyOnWriteArrayList<>();

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private ElevationClient clientFor(int status, int batchSize, boolean gap) throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/v1/srtm30m", exchange -> {
			String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
			String locations = query.substring(query.indexOf('=') + 1);
			locationsSeen.add(locations);
			List<String> results = new ArrayList<>();
			int index = 0;
			for (String location : locations.split("\\|")) {
				double latitude = Double.parseDouble(location.split(",")[0]);
				boolean missing = gap && index == 0;
				results.add("{\"elevation\":%s}".formatted(missing ? "null" : String.valueOf(latitude * -1000)));
				index++;
			}
			String body = "{\"status\":\"OK\",\"results\":[" + String.join(",", results) + "]}";
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		String url = "http://localhost:" + server.getAddress().getPort();
		ElevationProperties properties = new ElevationProperties(url, "srtm30m", batchSize, Duration.ofSeconds(2));
		return new ElevationClient(WebClient.builder().baseUrl(url).build(), properties);
	}

	@Test
	void returnsTheElevationOfEachPoint() throws IOException {
		ElevationClient client = clientFor(200, 100, false);
		GeoPoint a = new GeoPoint(-23.55, -46.63);
		GeoPoint b = new GeoPoint(-23.56, -46.64);

		Map<GeoPoint, Double> elevations = client.elevations(List.of(a, b));

		assertThat(elevations.get(a.snapped())).isEqualTo(23550.0);
		assertThat(elevations.get(b.snapped())).isEqualTo(23560.0);
	}

	@Test
	void splitsTheRequestsInBatches() throws IOException {
		ElevationClient client = clientFor(200, 2, false);
		List<GeoPoint> points = List.of(
			new GeoPoint(-23.51, -46.6), new GeoPoint(-23.52, -46.6), new GeoPoint(-23.53, -46.6));

		client.elevations(points);

		assertThat(locationsSeen).hasSize(2);
	}

	@Test
	void doesNotAskAgainForAPointItAlreadyKnows() throws IOException {
		ElevationClient client = clientFor(200, 100, false);
		GeoPoint a = new GeoPoint(-23.55, -46.63);

		client.elevations(List.of(a));
		client.elevations(List.of(a, new GeoPoint(-23.55001, -46.63001)));

		assertThat(locationsSeen).hasSize(1);
	}

	@Test
	void asksOnceForPointsThatLandOnTheSameCell() throws IOException {
		ElevationClient client = clientFor(200, 100, false);

		client.elevations(List.of(new GeoPoint(-23.55001, -46.63001), new GeoPoint(-23.55002, -46.63002)));

		assertThat(locationsSeen.get(0).split("\\|")).hasSize(1);
	}

	@Test
	void leavesOutThePointsThatHaveNoData() throws IOException {
		ElevationClient client = clientFor(200, 100, true);
		GeoPoint a = new GeoPoint(-23.55, -46.63);
		GeoPoint b = new GeoPoint(-23.56, -46.64);

		Map<GeoPoint, Double> elevations = client.elevations(List.of(a, b));

		assertThat(elevations).doesNotContainKey(a.snapped()).containsKey(b.snapped());
	}

	@Test
	void reportsAServiceThatFails() throws IOException {
		ElevationClient client = clientFor(500, 100, false);

		assertThatThrownBy(() -> client.elevations(List.of(new GeoPoint(-23.55, -46.63))))
			.isInstanceOf(ElevationUnavailableException.class);
	}

}
