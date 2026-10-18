package br.com.greenvrp.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.route.OperationStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExperimentServiceTest {

	private final ExperimentRepository repository = mock(ExperimentRepository.class);
	private final ExperimentRunner runner = mock(ExperimentRunner.class);
	private final ExperimentService service = new ExperimentService(repository, runner);

	private Experiment saved(Long id) {
		Experiment experiment = Experiment.pending(List.of(10, 15), List.of(1, 2, 3));
		ReflectionTestUtils.setField(experiment, "id", id);
		given(repository.save(any())).willReturn(experiment);
		return experiment;
	}

	@Test
	void startsAPendingExperimentAndRunsItInTheBackground() {
		saved(4L);

		ExperimentAcceptedResponse accepted = service.start(new ExperimentRequest(List.of(10, 15), List.of(1, 2, 3)));

		assertThat(accepted).isEqualTo(new ExperimentAcceptedResponse(4L, OperationStatus.PENDING));
		verify(runner).runAsync(4L);
	}

	@Test
	void announcesHowManyRunsItWillDo() {
		Experiment experiment = Experiment.pending(List.of(10, 15), List.of(1, 2, 3));

		assertThat(experiment.getTotalExecucoes()).isEqualTo(6);
		assertThat(experiment.getExecucoesConcluidas()).isZero();
		assertThat(experiment.getInstances()).containsExactly(10, 15);
		assertThat(experiment.getSeeds()).containsExactly(1, 2, 3);
	}

	@Test
	void refusesToStartWhileAnotherExperimentIsRunning() {
		given(repository.existsByStatus(OperationStatus.PENDING)).willReturn(true);

		assertThatThrownBy(() -> service.start(new ExperimentRequest(List.of(10), List.of(1))))
			.isInstanceOf(ExperimentAlreadyRunningException.class);
		verify(repository, never()).save(any());
		verify(runner, never()).runAsync(anyLong());
	}

	@Test
	void refusesRepeatedInstancesAndSeeds() {
		assertThatThrownBy(() -> service.start(new ExperimentRequest(List.of(10, 10), List.of(1))))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("instances must not contain duplicates");
		assertThatThrownBy(() -> service.start(new ExperimentRequest(List.of(10), List.of(1, 1))))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("seeds must not contain duplicates");
		verify(repository, never()).save(any());
	}

	@Test
	void returnsTheLatestExperiment() {
		Experiment experiment = saved(9L);
		given(repository.findFirstByOrderByIdDesc()).willReturn(Optional.of(experiment));

		ExperimentResponse response = service.latest();

		assertThat(response.id()).isEqualTo(9L);
		assertThat(response.status()).isEqualTo(OperationStatus.PENDING);
		assertThat(response.progress()).isEqualTo(new ExperimentResponse.Progress(0, 6));
	}

	@Test
	void failsWhenNoExperimentWasEverRun() {
		given(repository.findFirstByOrderByIdDesc()).willReturn(Optional.empty());

		assertThatThrownBy(service::latest)
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("No experiment has been run yet");
	}

}
