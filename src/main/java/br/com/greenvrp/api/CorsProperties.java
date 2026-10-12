package br.com.greenvrp.api;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lv.cors")
public record CorsProperties(
	@DefaultValue("http://localhost:5173") List<String> allowedOrigins,
	@DefaultValue("1h") Duration maxAge
) {
}
