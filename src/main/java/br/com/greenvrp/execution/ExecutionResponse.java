package br.com.greenvrp.execution;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.Operation;
import java.time.Instant;

public record ExecutionResponse(

	Long id,
	ExecutionStatus status,
	Instant scheduledAt,
	Instant createdAt,
	Instant startedAt,
	Instant finishedAt,
	Long operationId,
	OptimizationCriterion criterion,
	Long vehicleId,
	String vehiclePlate,
	int pointCount,
	Double totalEmissionKg,
	Double totalDistanceMeters,
	Double cargoKg

) {

	public static ExecutionResponse from(Execution execution) {
		Operation operation = execution.getOperation();
		return new ExecutionResponse(
			execution.getId(),
			execution.getStatus(),
			execution.getAgendadaPara(),
			execution.getCriadaEm(),
			execution.getIniciadaEm(),
			execution.getFinalizadaEm(),
			operation.getId(),
			operation.getCriterio(),
			operation.getVehicle().getId(),
			operation.getVehicle().getPlaca(),
			operation.getPoints().size(),
			operation.getEmissaoTotalKg(),
			operation.getDistanciaTotalMetros(),
			operation.getCargaKg());
	}

}
