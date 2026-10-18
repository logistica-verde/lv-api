package br.com.greenvrp.geo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lv.osrm")
public record OsrmProperties(
	@DefaultValue("http://localhost:5000") String url,
	@DefaultValue("driving") String profile,
	@DefaultValue("10s") Duration timeout
) {
}
