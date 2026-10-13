package br.com.greenvrp.geocode;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GeocoderClient {

	private static final String UNAVAILABLE = "The address search service is unavailable.";

	private final WebClient webClient;
	private final GeocodeProperties properties;

	public GeocoderClient(WebClient geocoderWebClient, GeocodeProperties properties) {
		this.webClient = geocoderWebClient;
		this.properties = properties;
	}

	public List<GeocodeMatch> search(String query) {
		String uri = UriComponentsBuilder.fromUriString(properties.url())
			.queryParam("q", "{q}")
			.queryParam("format", "jsonv2")
			.queryParam("limit", properties.limit())
			.queryParam("countrycodes", properties.countryCodes())
			.queryParam("accept-language", "pt-BR")
			.build(false)
			.toUriString();
		try {
			List<GeocodeMatch> matches = webClient.get()
				.uri(uri, query)
				.header(HttpHeaders.USER_AGENT, properties.userAgent())
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToFlux(GeocodeMatch.class)
				.collectList()
				.timeout(properties.timeout())
				.block();
			return matches == null ? List.of() : matches;
		}
		catch (RuntimeException failure) {
			throw new GeocoderUnavailableException(UNAVAILABLE, failure);
		}
	}

}
