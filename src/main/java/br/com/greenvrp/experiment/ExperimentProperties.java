package br.com.greenvrp.experiment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lv.experiments")
public record ExperimentProperties(
	@DefaultValue("7500") double vehicleMassKg
) {
}
