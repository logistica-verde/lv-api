package br.com.greenvrp.vehicle;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VehicleRequest(

	@NotBlank
	@Pattern(regexp = "(?i)^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$", message = "plate must follow the format ABC1234 or ABC1D23")
	String placa,

	@NotNull
	@Positive
	@DecimalMax("100000")
	Double massaKg,

	@NotBlank
	@Size(max = 30)
	String tipoCombustivel,

	VehicleImage imagem

) {
}
