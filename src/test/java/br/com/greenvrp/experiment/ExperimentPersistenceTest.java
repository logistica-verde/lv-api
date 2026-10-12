package br.com.greenvrp.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.OperationStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.DefaultApplicationArguments;

@DataJpaTest
class ExperimentPersistenceTest {

	@Autowired
	private ExperimentRepository repository;

	@Autowired
	private EntityManager entityManager;

	private Experiment reload(Long id) {
		entityManager.flush();
		entityManager.clear();
		return repository.findById(id).orElseThrow();
	}

	private ExperimentResult result(
		Experiment experiment, int size, int seed, OptimizationCriterion criterion, double emission, List<Double> curve) {
		return new ExperimentResult(experiment, size, seed, criterion, emission, emission * 100, 1500L, curve);
	}

	private Experiment finished(List<Integer> instances, List<Integer> seeds) {
		Experiment experiment = Experiment.pending(instances, seeds);
		for (int size : instances) {
			for (int seed : seeds) {
				experiment.addRun(
					result(experiment, size, seed, OptimizationCriterion.EMISSION, 90.0 + size, List.of(120.0, 90.0 + size)),
					result(experiment, size, seed, OptimizationCriterion.DISTANCE, 100.0 + size, List.of(30.0, 20.0)));
			}
		}
		experiment.complete();
		return experiment;
	}

	@Test
	void groupsEveryRunWithItsTwoCriteriaInOrder() {
		Experiment saved = repository.saveAndFlush(finished(List.of(15, 10), List.of(2, 1)));

		ExperimentResponse response = ExperimentResponse.from(reload(saved.getId()));

		assertThat(response.status()).isEqualTo(OperationStatus.COMPLETED);
		assertThat(response.progress()).isEqualTo(new ExperimentResponse.Progress(4, 4));
		assertThat(response.instances()).containsExactly(15, 10);
		assertThat(response.seeds()).containsExactly(2, 1);
		assertThat(response.runs())
			.extracting(ExperimentResponse.Run::instanceSize, ExperimentResponse.Run::seed)
			.containsExactly(
				tuple(10, 1), tuple(10, 2),
				tuple(15, 1), tuple(15, 2));
		ExperimentResponse.Run run = response.runs().get(0);
		assertThat(run.emission().emissionKg()).isEqualTo(100.0);
		assertThat(run.emission().convergence()).containsExactly(120.0, 100.0);
		assertThat(run.distance().emissionKg()).isEqualTo(110.0);
		assertThat(run.distance().distanceMeters()).isEqualTo(11000.0);
		assertThat(run.distance().executionMs()).isEqualTo(1500L);
	}

	@Test
	void leavesOutARunWhoseSecondCriterionIsMissing() {
		Experiment experiment = Experiment.pending(List.of(10), List.of(1));
		experiment.getResults();
		Experiment saved = repository.saveAndFlush(experiment);
		saved.addRun(
			result(saved, 10, 1, OptimizationCriterion.EMISSION, 90.0, List.of(90.0)),
			result(saved, 10, 1, OptimizationCriterion.DISTANCE, 100.0, List.of(20.0)));
		repository.saveAndFlush(saved);

		assertThat(ExperimentResponse.from(reload(saved.getId())).runs()).hasSize(1);
	}

	@Test
	void keepsALongConvergenceCurveIntact() {
		List<Double> curve = IntStream.range(0, 1000).mapToObj(index -> 5000.0 / (index + 1)).toList();
		Experiment experiment = Experiment.pending(List.of(10), List.of(1));
		experiment.addRun(
			result(experiment, 10, 1, OptimizationCriterion.EMISSION, 90.0, curve),
			result(experiment, 10, 1, OptimizationCriterion.DISTANCE, 100.0, curve));
		Experiment saved = repository.saveAndFlush(experiment);

		ExperimentResponse response = ExperimentResponse.from(reload(saved.getId()));

		assertThat(response.runs().get(0).emission().convergence()).isEqualTo(curve);
	}

	@Test
	void recordsAFailureWithATruncatedMessage() {
		Experiment experiment = Experiment.pending(List.of(10), List.of(1));
		experiment.fail("x".repeat(900));
		Experiment saved = repository.saveAndFlush(experiment);

		Experiment loaded = reload(saved.getId());

		assertThat(loaded.getStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(loaded.getMensagemErro()).hasSize(500);
	}

	@Test
	void findsTheLatestExperimentAndTheRunningOnes() {
		Experiment older = repository.saveAndFlush(finished(List.of(10), List.of(1)));
		Experiment newer = repository.saveAndFlush(Experiment.pending(List.of(10), List.of(1)));

		assertThat(repository.findFirstByOrderByIdDesc()).map(Experiment::getId).contains(newer.getId());
		assertThat(repository.existsByStatus(OperationStatus.PENDING)).isTrue();
		assertThat(repository.findByStatus(OperationStatus.COMPLETED)).extracting(Experiment::getId)
			.containsExactly(older.getId());
	}

	@Test
	void marksInterruptedExperimentsAsFailedOnStartup() {
		Experiment running = repository.saveAndFlush(Experiment.pending(List.of(10), List.of(1)));
		Experiment done = repository.saveAndFlush(finished(List.of(10), List.of(1)));

		new ExperimentRecovery(repository).run(new DefaultApplicationArguments());
		repository.flush();

		Experiment interrupted = reload(running.getId());
		assertThat(interrupted.getStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(interrupted.getMensagemErro()).contains("restarted");
		assertThat(reload(done.getId()).getStatus()).isEqualTo(OperationStatus.COMPLETED);
		assertThat(repository.existsByStatus(OperationStatus.PENDING)).isFalse();
	}

}
