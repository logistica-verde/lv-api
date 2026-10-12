package br.com.greenvrp.vehicle;

import jakarta.validation.constraints.NotNull;

public record VehicleStatusRequest(

	@NotNull
	Boolean ativo

) {
}
