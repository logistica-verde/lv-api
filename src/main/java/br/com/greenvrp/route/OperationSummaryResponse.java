package br.com.greenvrp.route;

import br.com.greenvrp.blockchain.IntegrityStatus;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.time.Instant;

public record OperationSummaryResponse(

	Long id,
	Instant createdAt,
	Long vehicleId,
	String vehiclePlate,
	int pointCount,
	OptimizationCriterion criterion,
	OperationStatus status,
	Double totalEmissionKg,
	Double totalDistanceMeters,
	IntegrityStatus integrityStatus

) {
}
