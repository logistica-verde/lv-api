package br.com.greenvrp.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRequest(

	@NotBlank
	@Size(min = 10, max = 500)
	String reason

) {
}
