package br.com.greenvrp.experiment;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.OperationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExperimentResponse(

	Long id,
	OperationStatus status,
	Instant createdAt,
	List<Integer> instances,
	List<Integer> seeds,
	Progress progress,
	List<Run> runs,
	String errorMessage

) {

	public record Progress(int completed, int total) {
	}

	public record CriterionResult(double emissionKg, double distanceMeters, long executionMs, List<Double> convergence) {

		static CriterionResult from(ExperimentResult result) {
			return new CriterionResult(
				result.getEmissionKg(), result.getDistanceMeters(), result.getExecutionMs(), result.getConvergence());
		}

	}

	public record Run(int instanceSize, int seed, CriterionResult emission, CriterionResult distance) {
	}

	private record RunKey(int instanceSize, int seed) {
	}

	public static ExperimentResponse from(Experiment experiment) {
		Map<RunKey, Map<OptimizationCriterion, ExperimentResult>> grouped = new LinkedHashMap<>();
		for (ExperimentResult result : experiment.getResults()) {
			grouped.computeIfAbsent(new RunKey(result.getInstanceSize(), result.getSeed()), key -> new LinkedHashMap<>())
				.put(result.getCriterion(), result);
		}
		List<Run> runs = new ArrayList<>();
		grouped.forEach((key, byCriterion) -> {
			ExperimentResult emission = byCriterion.get(OptimizationCriterion.EMISSION);
			ExperimentResult distance = byCriterion.get(OptimizationCriterion.DISTANCE);
			if (emission != null && distance != null) {
				runs.add(new Run(
					key.instanceSize(), key.seed(), CriterionResult.from(emission), CriterionResult.from(distance)));
			}
		});
		return new ExperimentResponse(
			experiment.getId(),
			experiment.getStatus(),
			experiment.getDataHora(),
			experiment.getInstances(),
			experiment.getSeeds(),
			new Progress(experiment.getExecucoesConcluidas(), experiment.getTotalExecucoes()),
			runs,
			experiment.getMensagemErro());
	}

}
