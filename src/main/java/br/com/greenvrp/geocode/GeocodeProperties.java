package br.com.greenvrp.geocode;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lv.geocoder")
public record GeocodeProperties(
	@DefaultValue("https://nominatim.openstreetmap.org/search") String url,
	@DefaultValue("lv-api/1.0 (Logistica Verde Inteligente)") String userAgent,
	@DefaultValue("5s") Duration timeout,
	@DefaultValue("1100ms") Duration minInterval,
	@DefaultValue("24h") Duration cacheTtl,
	@DefaultValue("500") int cacheSize,
	@DefaultValue("5") int limit,
	@DefaultValue("br") String countryCodes
) {
}
