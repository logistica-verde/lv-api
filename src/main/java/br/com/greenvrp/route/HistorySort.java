package br.com.greenvrp.route;

import br.com.greenvrp.api.InvalidRequestException;
import java.util.Arrays;

public enum HistorySort {

	CREATED_AT("createdAt", "dataHora"),
	TOTAL_EMISSION_KG("totalEmissionKg", "emissaoTotalKg"),
	TOTAL_DISTANCE_METERS("totalDistanceMeters", "distanciaTotalMetros");

	private final String key;
	private final String property;

	HistorySort(String key, String property) {
		this.key = key;
		this.property = property;
	}

	public String property() {
		return property;
	}

	public static HistorySort fromKey(String key) {
		return Arrays.stream(values())
			.filter(sort -> sort.key.equals(key))
			.findFirst()
			.orElseThrow(() -> new InvalidRequestException("Unknown sort field: " + key));
	}

}
