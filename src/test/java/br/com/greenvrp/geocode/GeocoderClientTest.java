package br.com.greenvrp.geocode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class GeocoderClientTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private record Seen(String query, String userAgent, String accept) {
	}

	private GeocoderClient clientFor(int status, String body, AtomicReference<Seen> seen, Duration timeout, long delayMillis)
		throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/search", exchange -> {
			if (seen != null) {
				seen.set(new Seen(
					exchange.getRequestURI().getRawQuery(),
					exchange.getRequestHeaders().getFirst("User-Agent"),
					exchange.getRequestHeaders().getFirst("Accept")));
			}
			try {
				Thread.sleep(delayMillis);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		GeocodeProperties properties = new GeocodeProperties(
			"http://localhost:" + server.getAddress().getPort() + "/search", "lv-test/1.0", timeout,
			Duration.ZERO, Duration.ofHours(1), 100, 5, "br");
		return new GeocoderClient(WebClient.builder().build(), properties);
	}

	@Test
	void asksTheProviderWithOurUserAgentAndTheSearchParameters() throws Exception {
		AtomicReference<Seen> seen = new AtomicReference<>();
		GeocoderClient client = clientFor(200, "[]", seen, Duration.ofSeconds(5), 0);

		client.search("Av. Paulista, 1578");

		assertThat(seen.get().userAgent()).isEqualTo("lv-test/1.0");
		assertThat(seen.get().accept()).contains("application/json");
		assertThat(seen.get().query())
			.contains("q=Av.%20Paulista%2C%201578")
			.contains("format=jsonv2")
			.contains("limit=5")
			.contains("countrycodes=br")
			.contains("accept-language=pt-BR");
	}

	@Test
	void parsesTheMatchesAndIgnoresTheOtherFields() throws Exception {
		String body = """
			[{"place_id":1,"display_name":"Praça da Sé, São Paulo","lat":"-23.5505","lon":"-46.6333","type":"square"},
			 {"place_id":2,"display_name":"Sé, Santos","lat":"-23.96","lon":"-46.33"}]
			""";
		GeocoderClient client = clientFor(200, body, null, Duration.ofSeconds(5), 0);

		List<GeocodeMatch> matches = client.search("Sé");

		assertThat(matches).containsExactly(
			new GeocodeMatch("Praça da Sé, São Paulo", "-23.5505", "-46.6333"),
			new GeocodeMatch("Sé, Santos", "-23.96", "-46.33"));
	}

	@Test
	void sendsSpecialCharactersWithoutBreakingTheQuery() throws Exception {
		AtomicReference<Seen> seen = new AtomicReference<>();
		GeocoderClient client = clientFor(200, "[]", seen, Duration.ofSeconds(5), 0);

		client.search("Rua A&B=C #1 + Água");

		assertThat(seen.get().query()).contains("q=Rua%20A%26B%3DC%20%231%20%2B%20%C3%81gua");
	}

	@Test
	void returnsNothingWhenThereAreNoMatches() throws Exception {
		assertThat(clientFor(200, "[]", null, Duration.ofSeconds(5), 0).search("Rua Inexistente")).isEmpty();
	}

	@Test
	void reportsTheServiceAsUnavailableOnHttpErrors() throws Exception {
		GeocoderClient client = clientFor(429, "{\"error\":\"rate limited\"}", null, Duration.ofSeconds(5), 0);

		assertThatThrownBy(() -> client.search("Sé"))
			.isInstanceOf(GeocoderUnavailableException.class)
			.hasMessage("The address search service is unavailable.");
	}

	@Test
	void reportsTheServiceAsUnavailableOnAnUnreadableAnswer() throws Exception {
		GeocoderClient client = clientFor(200, "<html>not json</html>", null, Duration.ofSeconds(5), 0);

		assertThatThrownBy(() -> client.search("Sé")).isInstanceOf(GeocoderUnavailableException.class);
	}

	@Test
	void reportsTheServiceAsUnavailableWhenItIsTooSlow() throws Exception {
		GeocoderClient client = clientFor(200, "[]", null, Duration.ofMillis(100), 600);

		assertThatThrownBy(() -> client.search("Sé")).isInstanceOf(GeocoderUnavailableException.class);
	}

	@Test
	void reportsTheServiceAsUnavailableWhenItCannotBeReached() throws Exception {
		GeocoderClient client = clientFor(200, "[]", null, Duration.ofSeconds(5), 0);
		server.stop(0);

		assertThatThrownBy(() -> client.search("Sé")).isInstanceOf(GeocoderUnavailableException.class);
	}

}
