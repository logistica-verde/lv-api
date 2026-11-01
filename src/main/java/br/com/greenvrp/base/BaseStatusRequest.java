package br.com.greenvrp.base;

import jakarta.validation.constraints.NotNull;

public record BaseStatusRequest(

	@NotNull
	Boolean ativo

) {
}
