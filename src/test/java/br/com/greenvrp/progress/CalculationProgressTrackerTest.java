package br.com.greenvrp.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class CalculationProgressTrackerTest {

	private final CalculationProgressTracker tracker = new CalculationProgressTracker();

	@Test
	void splitsThePercentBetweenTracingTheRoadsAndOptimizing() {
		assertThat(CalculationProgress.queued(1L).percent()).isZero();
		assertThat(CalculationProgress.preparing(1L, 45, 91).percent()).isCloseTo(12.36, org.assertj.core.data.Offset.offset(0.01));
		assertThat(CalculationProgress.preparing(1L, 91, 91).percent()).isEqualTo(25.0);
		assertThat(CalculationProgress.optimizing(1L, 0, 500).percent()).isEqualTo(25.0);
		assertThat(CalculationProgress.optimizing(1L, 250, 500).percent()).isEqualTo(60.0);
		assertThat(CalculationProgress.optimizing(1L, 500, 500).percent()).isEqualTo(95.0);
		assertThat(CalculationProgress.saving(1L).percent()).isEqualTo(95.0);
		assertThat(CalculationProgress.completed(1L).percent()).isEqualTo(100.0);
	}

	@Test
	void keepsTheLatestProgressOfEachOperation() {
		tracker.publish(CalculationProgress.optimizing(1L, 100, 500));
		tracker.publish(CalculationProgress.preparing(2L, 3, 7));

		assertThat(tracker.latest(1L)).contains(CalculationProgress.optimizing(1L, 100, 500));
		assertThat(tracker.latest(2L)).contains(CalculationProgress.preparing(2L, 3, 7));
		assertThat(tracker.latest(3L)).isEmpty();
	}

	@Test
	void tellsTheSubscribersOnlyAboutStepsOfAtLeastOnePercent() {
		List<CalculationProgress> received = new CopyOnWriteArrayList<>();
		tracker.subscribe(received::add);

		for (int generation = 0; generation <= 500; generation++) {
			tracker.publish(CalculationProgress.optimizing(1L, generation, 500));
		}

		assertThat(received).hasSizeBetween(60, 72);
		assertThat(received.get(0).done()).isZero();
		assertThat(received.get(received.size() - 1).done()).isEqualTo(500);
		assertThat(received).extracting(CalculationProgress::percent).isSorted();
	}

	@Test
	void alwaysTellsAboutANewStageAndTheEnd() {
		List<CalculationStage> received = new CopyOnWriteArrayList<>();
		tracker.subscribe(progress -> received.add(progress.stage()));

		tracker.publish(CalculationProgress.queued(1L));
		tracker.publish(CalculationProgress.queued(1L));
		tracker.publish(CalculationProgress.optimizing(1L, 500, 500));
		tracker.publish(CalculationProgress.saving(1L));
		tracker.publish(CalculationProgress.completed(1L));

		assertThat(received).containsExactly(
			CalculationStage.QUEUED, CalculationStage.OPTIMIZING, CalculationStage.SAVING, CalculationStage.COMPLETED);
	}

	@Test
	void stopsTellingASubscriberThatLeft() {
		List<CalculationProgress> received = new CopyOnWriteArrayList<>();
		Runnable unsubscribe = tracker.subscribe(received::add);

		tracker.publish(CalculationProgress.queued(1L));
		unsubscribe.run();
		tracker.publish(CalculationProgress.completed(1L));

		assertThat(received).hasSize(1);
	}

	@Test
	void forgetsTheOldestOperationsBeyondTheLimit() {
		for (long id = 1; id <= CalculationProgressTracker.REMEMBERED_OPERATIONS + 1; id++) {
			tracker.publish(CalculationProgress.completed(id));
		}

		assertThat(tracker.latest(1L)).isEmpty();
		assertThat(tracker.latest(2L)).isPresent();
	}

}
