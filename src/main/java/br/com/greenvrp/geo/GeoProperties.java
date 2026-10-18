package br.com.greenvrp.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lv.geo")
public record GeoProperties(
	@DefaultValue("database") String provider,
	@DefaultValue("100") double sampleStepMeters,
	@DefaultValue("8") int concurrency,
	@DefaultValue("5") double minSpeedKmh,
	@DefaultValue("90") double maxSpeedKmh,
	@DefaultValue("30") double fallbackSpeedKmh
) {
}
