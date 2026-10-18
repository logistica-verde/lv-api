package br.com.greenvrp.geocode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeocodeMatch(
	@JsonProperty("display_name") String displayName,
	String lat,
	String lon
) {
}
