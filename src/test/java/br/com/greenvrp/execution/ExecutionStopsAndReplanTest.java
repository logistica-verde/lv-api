package br.com.greenvrp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.OperationRepository;
import br.com.greenvrp.route.OperationSettledEvent;
import br.com.greenvrp.route.RouteProcessor;
import br.com.greenvrp.route.RouteService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({ExecutionService.class, ExecutionReplanListener.class, ExecutionStopsAndReplanTest.FixedClock.class})
class ExecutionStopsAndReplanTest {

	private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

	@TestConfiguration
	static class FixedClock {

		@Bean
		Clock executionClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}

	}

	@MockitoBean
	private RouteService routeService;

	@MockitoBean
	private RouteProcessor routeProcessor;

	@Autowired
	private ExecutionService service;

	@Autowired
	private ExecutionReplanListener listener;

	@Autowired
	private ExecutionRepository executionRepository;

	@Autowired
	private OperationRepository operationRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	private Vehicle vehicle;
	private List<DeliveryPoint> points;
	private Operation operation;

	@BeforeEach
	void setUp() {
		vehicle = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		points = List.of(
			deliveryPointRepository.save(new DeliveryPoint("Saída", null, -23.55, -46.63)),
			deliveryPointRepository.save(new DeliveryPoint("A", null, -23.56, -46.64)),
			deliveryPointRepository.save(new DeliveryPoint("B", null, -23.57, -46.65)),
			deliveryPointRepository.save(new DeliveryPoint("C", null, -23.58, -46.66)));
		Operation done = Operation.pending(vehicle, OptimizationCriterion.EMISSION, points);
		done.complete(5.5, List.of(0, 1, 2, 3), List.of());
		operation = operationRepository.save(done);
	}

	private ExecutionResponse started() {
		return service.create(new ExecutionRequest(operation.getId(), null));
	}

	private Long id(int index) {
		return points.get(index).getId();
	}

	@Test
	void createsOnePendingStopPerDeliveryExceptTheDeparture() {
		ExecutionResponse response = started();

		assertThat(response.stops()).extracting(ExecutionResponse.StopItem::name).containsExactly("A", "B", "C");
		assertThat(response.stops()).extracting(ExecutionResponse.StopItem::status)
			.containsOnly(StopStatus.PENDING);
		assertThat(response.stops()).extracting(ExecutionResponse.StopItem::order).containsExactly(1, 2, 3);
	}

	@Test
	void marksAStopAsDeliveredAndAnotherAsCancelledWithAReason() {
		ExecutionResponse created = started();

		service.deliverStop(created.id(), id(1));
		ExecutionResponse updated = service.cancelStop(created.id(), id(2), "  Cliente não estava no local  ");

		assertThat(updated.stops()).extracting(ExecutionResponse.StopItem::status)
			.containsExactly(StopStatus.DELIVERED, StopStatus.CANCELLED, StopStatus.PENDING);
		assertThat(updated.stops().get(1).reason()).isEqualTo("Cliente não estava no local");
	}

	@Test
	void aStopCanOnlyBeResolvedOnce() {
		ExecutionResponse created = started();
		service.deliverStop(created.id(), id(1));

		assertThatThrownBy(() -> service.deliverStop(created.id(), id(1)))
			.isInstanceOf(ExecutionStateException.class);
		assertThatThrownBy(() -> service.cancelStop(created.id(), id(1), "Motivo qualquer longo"))
			.isInstanceOf(ExecutionStateException.class);
	}

	@Test
	void stopsOnlyChangeWhileTheExecutionIsInProgress() {
		ExecutionResponse scheduled = service.create(
			new ExecutionRequest(operation.getId(), NOW.plusSeconds(3600)));

		assertThatThrownBy(() -> service.deliverStop(scheduled.id(), id(1)))
			.isInstanceOf(ExecutionStateException.class);
	}

	@Test
	void refusesAStopThatIsNotPartOfTheExecution() {
		ExecutionResponse created = started();

		assertThatThrownBy(() -> service.deliverStop(created.id(), 99_999L))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void replansTheRemainingStopsFromTheCurrentPosition() {
		ExecutionResponse created = started();
		service.deliverStop(created.id(), id(1));
		service.cancelStop(created.id(), id(2), "Cliente cancelou o pedido");
		Operation replacement = operationRepository.save(
			Operation.pending(vehicle, OptimizationCriterion.EMISSION, List.of(points.get(0), points.get(3))));
		when(routeService.createReplanOperation(any(), any(), any(), any(), anyList(), any())).thenReturn(replacement);

		ExecutionResponse replanning = service.replan(created.id(), -23.56, -46.64);

		assertThat(replanning.status()).isEqualTo(ExecutionStatus.REPLANNING);
		assertThat(replanning.revision()).isEqualTo(1);
		assertThat(replanning.operationId()).isEqualTo(replacement.getId());
		verify(routeService).createReplanOperation(
			any(), eq(OptimizationCriterion.EMISSION), any(), any(), eq(List.of(id(3))), any());
	}

	@Test
	void refusesToReplanWhenNothingIsPending() {
		ExecutionResponse created = started();
		service.deliverStop(created.id(), id(1));
		service.deliverStop(created.id(), id(2));
		service.deliverStop(created.id(), id(3));

		assertThatThrownBy(() -> service.replan(created.id(), -23.56, -46.64))
			.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void backToInProgressOnceTheNewRouteIsCalculated() {
		ExecutionResponse created = started();
		Operation replacement = operationRepository.save(
			Operation.pending(vehicle, OptimizationCriterion.EMISSION, List.of(points.get(0), points.get(3))));
		when(routeService.createReplanOperation(any(), any(), any(), any(), anyList(), any())).thenReturn(replacement);
		service.replan(created.id(), -23.56, -46.64);

		listener.onOperationSettled(new OperationSettledEvent(replacement.getId(), true, null));

		ExecutionResponse after = service.get(created.id());
		assertThat(after.status()).isEqualTo(ExecutionStatus.IN_PROGRESS);
		assertThat(after.operationId()).isEqualTo(replacement.getId());
		assertThat(after.replanMessage()).isNull();
	}

	@Test
	void keepsTheOldRouteWhenTheNewOneFails() {
		ExecutionResponse created = started();
		Operation replacement = operationRepository.save(
			Operation.pending(vehicle, OptimizationCriterion.EMISSION, List.of(points.get(0), points.get(3))));
		when(routeService.createReplanOperation(any(), any(), any(), any(), anyList(), any())).thenReturn(replacement);
		service.replan(created.id(), -23.56, -46.64);

		listener.onOperationSettled(new OperationSettledEvent(replacement.getId(), false, "Optimizer unavailable"));

		ExecutionResponse after = service.get(created.id());
		assertThat(after.status()).isEqualTo(ExecutionStatus.IN_PROGRESS);
		assertThat(after.operationId()).isEqualTo(operation.getId());
		assertThat(after.replanMessage()).isEqualTo("Optimizer unavailable");
		assertThat(after.revision()).isEqualTo(1);
	}

	@Test
	void ignoresAnOperationThatIsNotBeingReplanned() {
		ExecutionResponse created = started();

		listener.onOperationSettled(new OperationSettledEvent(operation.getId(), true, null));

		assertThat(service.get(created.id()).status()).isEqualTo(ExecutionStatus.IN_PROGRESS);
	}

	@Test
	void canBeCancelledWhileReplanning() {
		ExecutionResponse created = started();
		Operation replacement = operationRepository.save(
			Operation.pending(vehicle, OptimizationCriterion.EMISSION, List.of(points.get(0), points.get(3))));
		when(routeService.createReplanOperation(any(), any(), any(), any(), anyList(), any())).thenReturn(replacement);
		service.replan(created.id(), -23.56, -46.64);

		ExecutionResponse cancelled = service.cancel(created.id(), "Operação suspensa pela gerência", "ana");

		assertThat(cancelled.status()).isEqualTo(ExecutionStatus.CANCELLED);
		assertThat(executionRepository.findById(created.id())).isPresent();
	}

}
