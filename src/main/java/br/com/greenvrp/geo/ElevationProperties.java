package br.com.greenvrp.geo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lv.elevation")
public record ElevationProperties(
	@DefaultValue("http://localhost:5001") String url,
	@DefaultValue("srtm30m") String dataset,
	@DefaultValue("100") int batchSize,
	@DefaultValue("10s") Duration timeout
) {
}
