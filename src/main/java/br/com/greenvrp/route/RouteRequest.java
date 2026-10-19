package br.com.greenvrp.route;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RouteRequest(

	@NotNull
	Long vehicleId,

	@NotNull
	@Size(min = 1, max = 30)
	List<@NotNull Long> deliveryPointIds,

	@NotNull
	OptimizationCriterion criterion,

	@Valid
	Origin origin,

	@DecimalMin("0")
	@DecimalMax("100000")
	Double cargoKg

) {

	public record Origin(

		@NotNull
		@DecimalMin("-90")
		@DecimalMax("90")
		Double latitude,

		@NotNull
		@DecimalMin("-180")
		@DecimalMax("180")
		Double longitude

	) {
	}

	@JsonIgnore
	@AssertTrue(message = "at least two delivery points are required without an origin")
	public boolean isEnoughPoints() {
		return origin != null || deliveryPointIds == null || deliveryPointIds.size() >= 2;
	}

	public RouteRequest(Long vehicleId, List<Long> deliveryPointIds, OptimizationCriterion criterion) {
		this(vehicleId, deliveryPointIds, criterion, null, null);
	}

	public RouteRequest(
		Long vehicleId, List<Long> deliveryPointIds, OptimizationCriterion criterion, Origin origin) {
		this(vehicleId, deliveryPointIds, criterion, origin, null);
	}

}
