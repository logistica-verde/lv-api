package br.com.greenvrp.route;

import br.com.greenvrp.blockchain.IntegrityStatus;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.time.LocalDate;

public record HistoryQuery(

	LocalDate from,
	LocalDate to,
	Long vehicleId,
	OptimizationCriterion criterion,
	IntegrityStatus integrity,
	HistorySort sort,
	boolean ascending,
	int page,
	int size

) {
}
