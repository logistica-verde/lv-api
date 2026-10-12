package br.com.greenvrp.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VehicleRequest(

	@NotBlank
	String placa,

	@NotNull
	@Positive
	Double massaKg,

	@NotBlank
	String tipoCombustivel,

	VehicleImage imagem

) {
}
