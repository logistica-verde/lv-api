package br.com.greenvrp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.OperationRepository;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({ExecutionService.class, ExecutionServiceTest.FixedClock.class})
class ExecutionServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

	@TestConfiguration
	static class FixedClock {

		@Bean
		Clock executionClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

	@Autowired
	private ExecutionService service;

	@Autowired
	private OperationRepository operationRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	private Operation completed;
	private Operation pending;

	@BeforeEach
	void setUp() {
		Vehicle vehicle = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		List<DeliveryPoint> points = List.of(
			deliveryPointRepository.save(new DeliveryPoint("A", null, -23.55, -46.63)),
			deliveryPointRepository.save(new DeliveryPoint("B", null, -23.56, -46.64)));
		Operation done = Operation.pending(vehicle, OptimizationCriterion.EMISSION, points, 800.0);
		done.complete(5.5, List.of(0, 1), List.of());
		completed = operationRepository.save(done);
		pending = operationRepository.save(Operation.pending(vehicle, OptimizationCriterion.DISTANCE, points));
	}

	@Test
	void startsRightAwayWhenThereIsNoScheduledTime() {
		ExecutionResponse response = service.create(new ExecutionRequest(completed.getId(), null));

		assertThat(response.status()).isEqualTo(ExecutionStatus.IN_PROGRESS);
		assertThat(response.startedAt()).isEqualTo(NOW);
		assertThat(response.scheduledAt()).isNull();
		assertThat(response.operationId()).isEqualTo(completed.getId());
		assertThat(response.vehiclePlate()).isEqualTo("ABC1D23");
		assertThat(response.cargoKg()).isEqualTo(800.0);
		assertThat(response.totalEmissionKg()).isEqualTo(5.5);
	}

	@Test
	void schedulesForALaterTime() {
		Instant later = NOW.plusSeconds(3600);

		ExecutionResponse response = service.create(new ExecutionRequest(completed.getId(), later));

		assertThat(response.status()).isEqualTo(ExecutionStatus.SCHEDULED);
		assertThat(response.scheduledAt()).isEqualTo(later);
		assertThat(response.startedAt()).isNull();
	}

	@Test
	void refusesATimeInThePastButToleratesAMinuteOfClockSkew() {
		assertThatThrownBy(() -> service.create(new ExecutionRequest(completed.getId(), NOW.minusSeconds(600))))
			.isInstanceOf(InvalidRequestException.class);

		ExecutionResponse response = service.create(new ExecutionRequest(completed.getId(), NOW.minusSeconds(30)));

		assertThat(response.status()).isEqualTo(ExecutionStatus.SCHEDULED);
	}

	@Test
	void refusesARouteThatIsNotCompletedOrDoesNotExist() {
		assertThatThrownBy(() -> service.create(new ExecutionRequest(pending.getId(), null)))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.create(new ExecutionRequest(9999L, null)))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void listsTheScheduledExecutionsByTime() {
		ExecutionResponse late = service.create(new ExecutionRequest(completed.getId(), NOW.plusSeconds(7200)));
		ExecutionResponse early = service.create(new ExecutionRequest(completed.getId(), NOW.plusSeconds(600)));
		service.create(new ExecutionRequest(completed.getId(), null));

		List<ExecutionResponse> scheduled = service.list(ExecutionStatus.SCHEDULED);

		assertThat(scheduled).extracting(ExecutionResponse::id).containsExactly(early.id(), late.id());
		assertThat(service.list(ExecutionStatus.IN_PROGRESS)).hasSize(1);
	}

	@Test
	void startsAScheduledExecution() {
		ExecutionResponse scheduled = service.create(new ExecutionRequest(completed.getId(), NOW.plusSeconds(600)));

		ExecutionResponse started = service.start(scheduled.id());

		assertThat(started.status()).isEqualTo(ExecutionStatus.IN_PROGRESS);
		assertThat(started.startedAt()).isEqualTo(NOW);
		assertThatThrownBy(() -> service.start(scheduled.id())).isInstanceOf(ExecutionStateException.class);
	}

	@Test
	void reschedulesOnlyWhileScheduled() {
		ExecutionResponse scheduled = service.create(new ExecutionRequest(completed.getId(), NOW.plusSeconds(600)));
		Instant newTime = NOW.plusSeconds(86_400);

		ExecutionResponse moved = service.reschedule(scheduled.id(), newTime);

		assertThat(moved.scheduledAt()).isEqualTo(newTime);
		assertThatThrownBy(() -> service.reschedule(scheduled.id(), NOW.minusSeconds(3600)))
			.isInstanceOf(InvalidRequestException.class);
		service.start(scheduled.id());
		assertThatThrownBy(() -> service.reschedule(scheduled.id(), newTime))
			.isInstanceOf(ExecutionStateException.class);
	}

	@Test
	void finishesOnlyWhatIsInProgress() {
		ExecutionResponse scheduled = service.create(new ExecutionRequest(completed.getId(), NOW.plusSeconds(600)));
		assertThatThrownBy(() -> service.finish(scheduled.id())).isInstanceOf(ExecutionStateException.class);
		ExecutionResponse running = service.create(new ExecutionRequest(completed.getId(), null));

		ExecutionResponse finished = service.finish(running.id());

		assertThat(finished.status()).isEqualTo(ExecutionStatus.FINISHED);
		assertThat(finished.finishedAt()).isEqualTo(NOW);
	}

	@Test
	void cancelsAnOpenExecutionButNotAClosedOne() {
		ExecutionResponse scheduled = service.create(new ExecutionRequest(completed.getId(), NOW.plusSeconds(600)));

		assertThat(service.cancel(scheduled.id()).status()).isEqualTo(ExecutionStatus.CANCELLED);
		assertThatThrownBy(() -> service.cancel(scheduled.id())).isInstanceOf(ExecutionStateException.class);
	}

	@Test
	void reportsAnUnknownExecution() {
		assertThatThrownBy(() -> service.get(404L)).isInstanceOf(ResourceNotFoundException.class);
	}

}
