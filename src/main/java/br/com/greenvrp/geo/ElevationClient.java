package br.com.greenvrp.geo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ElevationClient {

	private static final String UNAVAILABLE = "The elevation service is unavailable.";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Response(String status, List<Result> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Result(Double elevation) {
	}

	private final WebClient webClient;
	private final ElevationProperties properties;
	private final Map<GeoPoint, Double> known = new ConcurrentHashMap<>();

	public ElevationClient(WebClient elevationWebClient, ElevationProperties properties) {
		this.webClient = elevationWebClient;
		this.properties = properties;
	}

	public Map<GeoPoint, Double> elevations(List<GeoPoint> points) {
		List<GeoPoint> missing = points.stream().map(GeoPoint::snapped).distinct()
			.filter(point -> !known.containsKey(point)).toList();
		for (int from = 0; from < missing.size(); from += properties.batchSize()) {
			List<GeoPoint> batch = missing.subList(from, Math.min(missing.size(), from + properties.batchSize()));
			List<Double> values = fetch(batch);
			for (int index = 0; index < batch.size(); index++) {
				Double value = values.get(index);
				if (value != null) {
					known.put(batch.get(index), value);
				}
			}
		}
		Map<GeoPoint, Double> found = new HashMap<>();
		for (GeoPoint point : points) {
			Double value = known.get(point.snapped());
			if (value != null) {
				found.put(point.snapped(), value);
			}
		}
		return found;
	}

	private List<Double> fetch(List<GeoPoint> batch) {
		String locations = batch.stream()
			.map(point -> "%s,%s".formatted(point.latitude(), point.longitude()))
			.collect(Collectors.joining("|"));
		Response response;
		try {
			response = webClient.get()
				.uri("/v1/{dataset}?locations={locations}", properties.dataset(), locations)
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.bodyToMono(Response.class)
				.timeout(properties.timeout())
				.block();
		}
		catch (RuntimeException failure) {
			throw new ElevationUnavailableException(UNAVAILABLE, failure);
		}
		if (response == null || !"OK".equals(response.status()) || response.results() == null
			|| response.results().size() != batch.size()) {
			throw new ElevationUnavailableException(UNAVAILABLE, null);
		}
		List<Double> values = new ArrayList<>();
		response.results().forEach(result -> values.add(result.elevation()));
		return values;
	}

}
