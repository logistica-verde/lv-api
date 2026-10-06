package br.com.greenvrp.optimizer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "optimizer")
public record OptimizerProperties(
	@DefaultValue("http://localhost:8000") String url,
	@DefaultValue("90s") Duration timeout
) {
}
