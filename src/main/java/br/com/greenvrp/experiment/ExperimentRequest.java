package br.com.greenvrp.experiment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ExperimentRequest(

	@NotNull
	@Size(min = 1, max = 10)
	List<@NotNull @Min(2) @Max(30) Integer> instances,

	@NotNull
	@Size(min = 1, max = 10)
	List<@NotNull @Min(1) Integer> seeds

) {
}
