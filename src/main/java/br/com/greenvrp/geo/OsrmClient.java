package br.com.greenvrp.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OsrmClient {

	private static final String ROUTE_PATH = "/route/v1/%s/%s?overview=full&geometries=geojson&steps=false";
	private static final String UNAVAILABLE = "The routing service is unavailable.";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(String code, List<Route> routes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Route(double distance, double duration, Geometry geometry) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Geometry(List<List<Double>> coordinates) {
	}

	private final WebClient webClient;
	private final OsrmProperties properties;

	public OsrmClient(WebClient osrmWebClient, OsrmProperties properties) {
		this.webClient = osrmWebClient;
		this.properties = properties;
	}

	public RoadPath route(GeoPoint from, GeoPoint to) {
		Response response;
		try {
			response = webClient.get()
				.uri(ROUTE_PATH.formatted(properties.profile(), coordinates(from, to)))
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToMono(Response.class)
				.timeout(properties.timeout())
				.block();
		}
		catch (RuntimeException failure) {
			throw new RoutingUnavailableException(UNAVAILABLE, failure);
		}
		if (response == null || !"Ok".equals(response.code()) || response.routes() == null
			|| response.routes().isEmpty()) {
			throw new RoadDataNotFoundException("No road route found between the two delivery points");
		}
		Route route = response.routes().get(0);
		return new RoadPath(route.distance(), route.duration(), toPoints(route.geometry()));
	}

	private static String coordinates(GeoPoint from, GeoPoint to) {
		return "%s,%s;%s,%s".formatted(from.longitude(), from.latitude(), to.longitude(), to.latitude());
	}

	private static List<GeoPoint> toPoints(Geometry geometry) {
		if (geometry == null || geometry.coordinates() == null) {
			return List.of();
		}
		return geometry.coordinates().stream()
			.map(pair -> new GeoPoint(pair.get(1), pair.get(0)))
			.toList();
	}

}
