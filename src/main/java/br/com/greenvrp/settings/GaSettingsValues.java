package br.com.greenvrp.settings;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GaSettingsValues(

	@NotNull
	@Min(10)
	@Max(500)
	Integer populationSize,

	@NotNull
	@Min(10)
	@Max(1000)
	Integer generations,

	@NotNull
	@DecimalMin("0.0")
	@DecimalMax("1.0")
	Double crossoverRate,

	@NotNull
	@DecimalMin("0.0")
	@DecimalMax("1.0")
	Double mutationRate

) {

	public static final GaSettingsValues DEFAULT = new GaSettingsValues(300, 500, 0.9, 0.4);

}
