package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class OsrmClientTest {

	private static final String OK = """
		{"code":"Ok","routes":[{"distance":1523.4,"duration":181.2,
		"geometry":{"type":"LineString","coordinates":[[-46.63,-23.55],[-46.64,-23.56],[-46.65,-23.57]]},
		"legs":[]}],"waypoints":[]}
		""";

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private OsrmClient clientFor(int status, String body, AtomicReference<String> seenUri) throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			if (seenUri != null) {
				seenUri.set(exchange.getRequestURI().toString());
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		String url = "http://localhost:" + server.getAddress().getPort();
		OsrmProperties properties = new OsrmProperties(url, "driving", Duration.ofSeconds(2));
		return new OsrmClient(WebClient.builder().baseUrl(url).build(), properties);
	}

	@Test
	void readsTheDistanceTheDurationAndTheGeometryOfTheRoute() throws IOException {
		OsrmClient client = clientFor(200, OK, null);

		RoadPath path = client.route(new GeoPoint(-23.55, -46.63), new GeoPoint(-23.57, -46.65));

		assertThat(path.distanceMeters()).isCloseTo(1523.4, within(1e-9));
		assertThat(path.durationSeconds()).isCloseTo(181.2, within(1e-9));
		assertThat(path.geometry()).containsExactly(
			new GeoPoint(-23.55, -46.63), new GeoPoint(-23.56, -46.64), new GeoPoint(-23.57, -46.65));
	}

	@Test
	void asksForTheFullGeometryWithLongitudeFirst() throws IOException {
		AtomicReference<String> uri = new AtomicReference<>();
		OsrmClient client = clientFor(200, OK, uri);

		client.route(new GeoPoint(-23.55, -46.63), new GeoPoint(-23.57, -46.65));

		assertThat(uri.get()).startsWith("/route/v1/driving/-46.63,-23.55;-46.65,-23.57");
		assertThat(uri.get()).contains("overview=full").contains("geometries=geojson");
	}

	@Test
	void reportsPointsThatHaveNoRoadBetweenThem() throws IOException {
		OsrmClient client = clientFor(200, "{\"code\":\"NoRoute\",\"routes\":[]}", null);

		assertThatThrownBy(() -> client.route(new GeoPoint(0, 0), new GeoPoint(1, 1)))
			.isInstanceOf(RoadDataNotFoundException.class);
	}

	@Test
	void reportsAServiceThatFails() throws IOException {
		OsrmClient client = clientFor(500, "{}", null);

		assertThatThrownBy(() -> client.route(new GeoPoint(0, 0), new GeoPoint(1, 1)))
			.isInstanceOf(RoutingUnavailableException.class);
	}

	@Test
	void reportsAServiceThatIsDown() {
		OsrmProperties properties = new OsrmProperties("http://localhost:1", "driving", Duration.ofSeconds(1));
		OsrmClient client = new OsrmClient(WebClient.builder().baseUrl(properties.url()).build(), properties);

		assertThatThrownBy(() -> client.route(new GeoPoint(0, 0), new GeoPoint(1, 1)))
			.isInstanceOf(RoutingUnavailableException.class);
	}

}
