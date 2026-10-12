package br.com.greenvrp.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.optimizer.OptimizeRequest;
import br.com.greenvrp.optimizer.OptimizeResponse;
import br.com.greenvrp.optimizer.OptimizerClient;
import br.com.greenvrp.optimizer.OptimizerException;
import br.com.greenvrp.route.OperationStatus;
import br.com.greenvrp.settings.GaSettingsService;
import br.com.greenvrp.settings.GaSettingsValues;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExperimentRunnerTest {

	@Autowired
	private ExperimentRepository repository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final OptimizerClient optimizerClient = mock(OptimizerClient.class);
	private final GaSettingsService gaSettingsService = mock(GaSettingsService.class);

	private ExperimentRunner runner;

	@BeforeEach
	void setUp() {
		when(gaSettingsService.current()).thenReturn(new GaSettingsValues(120, 240, 0.7, 0.03));
		runner = new ExperimentRunner(
			repository, optimizerClient, gaSettingsService, new ExperimentProperties(7500.0), transactionManager,
			Runnable::run);
	}

	@AfterEach
	void cleanUp() {
		repository.deleteAll();
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		return new TransactionTemplate(transactionManager).execute(status -> action.get());
	}

	private OptimizeResponse answerFor(OptimizeRequest request) {
		boolean emission = request.criterion().equals("emission");
		return new OptimizeResponse(
			List.of(0, 1), emission ? 90.0 : 100.0, emission ? 12000.0 : 10000.0, List.of(),
			emission ? List.of(120.0, 100.0, 90.0) : List.of(26.0, 22.0, 20.0));
	}

	private Long pendingExperiment(List<Integer> instances, List<Integer> seeds) {
		return repository.save(Experiment.pending(instances, seeds)).getId();
	}

	@Test
	void runsEveryInstanceAndSeedForBothCriteriaAndStoresWhatTheOptimizerReturned() {
		when(optimizerClient.optimize(any())).thenAnswer(
			invocation -> CompletableFuture.completedFuture(answerFor(invocation.getArgument(0))));
		Long id = pendingExperiment(List.of(5, 6), List.of(1, 2));

		runner.runAsync(id).join();

		inTransaction(() -> {
			Experiment experiment = repository.findById(id).orElseThrow();
			assertThat(experiment.getStatus()).isEqualTo(OperationStatus.COMPLETED);
			assertThat(experiment.getExecucoesConcluidas()).isEqualTo(4);
			assertThat(experiment.getTotalExecucoes()).isEqualTo(4);
			assertThat(experiment.getMensagemErro()).isNull();
			assertThat(experiment.getResults())
				.extracting(ExperimentResult::getInstanceSize, ExperimentResult::getSeed)
				.containsExactlyInAnyOrder(
					tuple(5, 1), tuple(5, 1), tuple(5, 2), tuple(5, 2),
					tuple(6, 1), tuple(6, 1), tuple(6, 2), tuple(6, 2));
			ExperimentResult emission = experiment.getResults().stream()
				.filter(result -> result.getCriterion() == OptimizationCriterion.EMISSION)
				.findFirst().orElseThrow();
			assertThat(emission.getEmissionKg()).isEqualTo(90.0);
			assertThat(emission.getDistanceMeters()).isEqualTo(12000.0);
			assertThat(emission.getConvergence()).containsExactly(120.0, 100.0, 90.0);
			assertThat(emission.getExecutionMs()).isGreaterThanOrEqualTo(0);
			return null;
		});
	}

	@Test
	void sendsTheSeedTheSizeTheVehicleMassAndTheConfiguredParameters() {
		when(optimizerClient.optimize(any())).thenAnswer(
			invocation -> CompletableFuture.completedFuture(answerFor(invocation.getArgument(0))));
		Long id = pendingExperiment(List.of(5), List.of(7));

		runner.runAsync(id).join();

		ArgumentCaptor<OptimizeRequest> sent = ArgumentCaptor.forClass(OptimizeRequest.class);
		verify(optimizerClient, times(2)).optimize(sent.capture());
		assertThat(sent.getAllValues())
			.extracting(OptimizeRequest::criterion)
			.containsExactly("emission", "distance");
		assertThat(sent.getAllValues()).allSatisfy(request -> {
			assertThat(request.seed()).isEqualTo(7L);
			assertThat(request.pointCount()).isEqualTo(5);
			assertThat(request.vehicleMassKg()).isEqualTo(7500.0);
			assertThat(request.segments()).hasSize(20);
			assertThat(request.gaParams()).isEqualTo(new OptimizeRequest.GaParams(120, 240, 0.7, 0.03));
		});
		assertThat(sent.getAllValues().get(0).segments()).isEqualTo(sent.getAllValues().get(1).segments());
	}

	@Test
	void keepsTheRunsDoneSoFarAndRecordsTheFailure() {
		when(optimizerClient.optimize(any()))
			.thenAnswer(invocation -> CompletableFuture.completedFuture(answerFor(invocation.getArgument(0))))
			.thenAnswer(invocation -> CompletableFuture.completedFuture(answerFor(invocation.getArgument(0))))
			.thenReturn(CompletableFuture.failedFuture(
				new OptimizerException("Optimizer call failed: connection refused", new RuntimeException())));
		Long id = pendingExperiment(List.of(5, 6), List.of(1));

		runner.runAsync(id).join();

		inTransaction(() -> {
			Experiment experiment = repository.findById(id).orElseThrow();
			assertThat(experiment.getStatus()).isEqualTo(OperationStatus.FAILED);
			assertThat(experiment.getMensagemErro()).contains("connection refused");
			assertThat(experiment.getExecucoesConcluidas()).isEqualTo(1);
			assertThat(experiment.getResults()).hasSize(2);
			return null;
		});
		verify(optimizerClient, times(3)).optimize(any());
	}

	@Test
	void storesAnEmptyCurveWhenTheOptimizerSendsNone() {
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.completedFuture(
			new OptimizeResponse(List.of(0, 1), 90.0, 12000.0, List.of())));
		Long id = pendingExperiment(List.of(5), List.of(1));

		runner.runAsync(id).join();

		inTransaction(() -> {
			Experiment experiment = repository.findById(id).orElseThrow();
			assertThat(experiment.getStatus()).isEqualTo(OperationStatus.COMPLETED);
			assertThat(experiment.getResults()).allSatisfy(result -> assertThat(result.getConvergence()).isEmpty());
			return null;
		});
	}

	@Test
	void recordsAFailureThatHasNoMessage() {
		when(optimizerClient.optimize(any())).thenThrow(new IllegalStateException());
		Long id = pendingExperiment(List.of(5), List.of(1));

		runner.runAsync(id).join();

		inTransaction(() -> {
			Experiment experiment = repository.findById(id).orElseThrow();
			assertThat(experiment.getStatus()).isEqualTo(OperationStatus.FAILED);
			assertThat(experiment.getMensagemErro()).isEqualTo("IllegalStateException");
			return null;
		});
		verify(optimizerClient, atLeastOnce()).optimize(any());
	}

	@Test
	void doesNotFailForAnExperimentThatDoesNotExistAnymore() {
		runner.runAsync(9999L).join();

		verify(optimizerClient, times(0)).optimize(any());
	}

}
