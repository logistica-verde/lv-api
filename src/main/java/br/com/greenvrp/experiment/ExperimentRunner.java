package br.com.greenvrp.experiment;

import br.com.greenvrp.geo.PairSegment;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.optimizer.OptimizeRequest;
import br.com.greenvrp.optimizer.OptimizeResponse;
import br.com.greenvrp.optimizer.OptimizerClient;
import br.com.greenvrp.settings.GaSettingsService;
import br.com.greenvrp.settings.GaSettingsValues;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ExperimentRunner {

	private static final Logger log = LoggerFactory.getLogger(ExperimentRunner.class);

	private record Timed(OptimizeResponse response, long executionMs) {
	}

	private record Plan(List<Integer> instances, List<Integer> seeds) {
	}

	private final ExperimentRepository repository;
	private final OptimizerClient optimizerClient;
	private final GaSettingsService gaSettingsService;
	private final ExperimentProperties properties;
	private final TransactionTemplate transactions;
	private final Executor executor;

	public ExperimentRunner(
		ExperimentRepository repository,
		OptimizerClient optimizerClient,
		GaSettingsService gaSettingsService,
		ExperimentProperties properties,
		PlatformTransactionManager transactionManager,
		@Qualifier("experimentExecutor") Executor executor) {
		this.repository = repository;
		this.optimizerClient = optimizerClient;
		this.gaSettingsService = gaSettingsService;
		this.properties = properties;
		this.transactions = new TransactionTemplate(transactionManager);
		this.executor = executor;
	}

	public CompletableFuture<Void> runAsync(Long experimentId) {
		return CompletableFuture.runAsync(() -> execute(experimentId), executor);
	}

	private void execute(Long experimentId) {
		try {
			Plan plan = transactions.execute(status -> {
				Experiment experiment = repository.findById(experimentId).orElseThrow();
				return new Plan(experiment.getInstances(), experiment.getSeeds());
			});
			GaSettingsValues settings = gaSettingsService.current();
			OptimizeRequest.GaParams gaParams = new OptimizeRequest.GaParams(
				settings.populationSize(), settings.generations(), settings.crossoverRate(), settings.mutationRate());

			for (int size : plan.instances()) {
				for (int seed : plan.seeds()) {
					List<PairSegment> pairs = InstanceGenerator.pairSegments(size, seed);
					Timed emission = optimize(OptimizationCriterion.EMISSION, size, seed, pairs, gaParams);
					Timed distance = optimize(OptimizationCriterion.DISTANCE, size, seed, pairs, gaParams);
					saveRun(experimentId, size, seed, emission, distance);
				}
			}
			transactions.executeWithoutResult(status -> {
				Experiment experiment = repository.findById(experimentId).orElseThrow();
				experiment.complete();
				repository.save(experiment);
			});
		}
		catch (Exception error) {
			markFailed(experimentId, error);
		}
	}

	private Timed optimize(
		OptimizationCriterion criterion, int size, int seed, List<PairSegment> pairs, OptimizeRequest.GaParams gaParams) {
		OptimizeRequest request = OptimizeRequest.of(
			criterion, properties.vehicleMassKg(), size, pairs, gaParams, (long) seed);
		long startedAt = System.nanoTime();
		OptimizeResponse response = optimizerClient.optimize(request).join();
		return new Timed(response, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
	}

	private void saveRun(Long experimentId, int size, int seed, Timed emission, Timed distance) {
		transactions.executeWithoutResult(status -> {
			Experiment experiment = repository.findById(experimentId).orElseThrow();
			experiment.addRun(
				result(experiment, size, seed, OptimizationCriterion.EMISSION, emission),
				result(experiment, size, seed, OptimizationCriterion.DISTANCE, distance));
			repository.save(experiment);
		});
	}

	private ExperimentResult result(
		Experiment experiment, int size, int seed, OptimizationCriterion criterion, Timed timed) {
		OptimizeResponse response = timed.response();
		List<Double> convergence = response.convergence() != null ? response.convergence() : List.of();
		return new ExperimentResult(
			experiment, size, seed, criterion, response.totalEmissionKg(), response.totalDistanceMeters(),
			timed.executionMs(), convergence);
	}

	private void markFailed(Long experimentId, Throwable error) {
		Throwable root = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		String message = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
		log.warn("Experiment {} failed: {}", experimentId, message);
		try {
			transactions.executeWithoutResult(status -> repository.findById(experimentId).ifPresent(experiment -> {
				experiment.fail(message);
				repository.save(experiment);
			}));
		}
		catch (RuntimeException saveError) {
			log.error("Could not record the failure of experiment {}", experimentId, saveError);
		}
	}

}
