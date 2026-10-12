package br.com.greenvrp.route;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lv.history")
public record HistoryProperties(
	@DefaultValue("America/Sao_Paulo") ZoneId zone,
	@DefaultValue("1000") int integrityFilterLimit
) {
}
