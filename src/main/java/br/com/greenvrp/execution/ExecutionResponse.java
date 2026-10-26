package br.com.greenvrp.execution;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.Operation;
import java.time.Instant;
import java.util.List;

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
	Double cargoKg,
	String cancellationReason,
	String cancelledBy,
	int revision,
	String replanMessage,
	List<StopItem> stops

) {

	public record StopItem(
		Long pointId,
		String name,
		int order,
		StopStatus status,
		String reason
	) {
	}

	public ExecutionResponse(
		Long id, ExecutionStatus status, Instant scheduledAt, Instant createdAt, Instant startedAt,
		Instant finishedAt, Long operationId, OptimizationCriterion criterion, Long vehicleId,
		String vehiclePlate, int pointCount, Double totalEmissionKg, Double totalDistanceMeters,
		Double cargoKg, String cancellationReason, String cancelledBy) {
		this(id, status, scheduledAt, createdAt, startedAt, finishedAt, operationId, criterion, vehicleId,
			vehiclePlate, pointCount, totalEmissionKg, totalDistanceMeters, cargoKg, cancellationReason,
			cancelledBy, 0, null, List.of());
	}

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
			operation.getCargaKg(),
			execution.getMotivoCancelamento(),
			execution.getCanceladaPor(),
			execution.getRevisao(),
			execution.getMensagemReplanejamento(),
			execution.getStops().stream()
				.map(stop -> new StopItem(
					stop.getPoint().getId(), stop.getPoint().getNome(), stop.getOrdem(), stop.getStatus(),
					stop.getMotivo()))
				.toList());
	}

}
