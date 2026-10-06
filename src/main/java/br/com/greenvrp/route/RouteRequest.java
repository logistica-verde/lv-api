package br.com.greenvrp.route;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RouteRequest(

	@NotNull
	Long vehicleId,

	@NotNull
	@Size(min = 2, max = 30)
	List<@NotNull Long> deliveryPointIds,

	@NotNull
	OptimizationCriterion criterion

) {
}
