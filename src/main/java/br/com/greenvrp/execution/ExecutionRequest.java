package br.com.greenvrp.execution;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ExecutionRequest(

	@NotNull
	Long operationId,

	Instant scheduledAt

) {
}
