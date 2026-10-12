package br.com.greenvrp.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RouteComparisonTest {

	@Autowired
	private OperationRepository operationRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	private RouteService service;
	private Vehicle vehicle;
	private List<DeliveryPoint> points;

	@BeforeEach
	void setUp() {
		service = new RouteService(vehicleRepository, deliveryPointRepository, operationRepository, mock(RouteProcessor.class));
		vehicle = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		points = List.of(
			deliveryPointRepository.save(new DeliveryPoint("P1", null, -23.55, -46.63)),
			deliveryPointRepository.save(new DeliveryPoint("P2", null, -23.56, -46.64)),
			deliveryPointRepository.save(new DeliveryPoint("P3", null, -23.57, -46.65)));
	}

	private Operation operation(Vehicle forVehicle, OptimizationCriterion criterion, List<DeliveryPoint> forPoints) {
		return operationRepository.save(Operation.pending(forVehicle, criterion, forPoints));
	}

	@Test
	void returnsBothRoutesWhenTheyShareTheVehicleAndThePoints() {
		Operation emission = operation(vehicle, OptimizationCriterion.EMISSION, points);
		Operation distance = operation(vehicle, OptimizationCriterion.DISTANCE, points);
		emission.registrarTempoExecucao(842);
		distance.registrarTempoExecucao(615);
		operationRepository.flush();

		RouteComparisonResponse comparison = service.compare(emission.getId(), distance.getId());

		assertThat(comparison.emission().id()).isEqualTo(emission.getId());
		assertThat(comparison.emission().criterion()).isEqualTo(OptimizationCriterion.EMISSION);
		assertThat(comparison.emission().executionMs()).isEqualTo(842);
		assertThat(comparison.distance().id()).isEqualTo(distance.getId());
		assertThat(comparison.distance().criterion()).isEqualTo(OptimizationCriterion.DISTANCE);
		assertThat(comparison.distance().executionMs()).isEqualTo(615);
	}

	@Test
	void doesNotCareInWhichOrderThePointsWereGiven() {
		Operation emission = operation(vehicle, OptimizationCriterion.EMISSION, points);
		Operation distance = operation(vehicle, OptimizationCriterion.DISTANCE, List.of(points.get(2), points.get(0), points.get(1)));

		assertThat(service.compare(emission.getId(), distance.getId()).distance().id()).isEqualTo(distance.getId());
	}

	@Test
	void refusesRoutesCalculatedForDifferentVehicles() {
		Vehicle other = vehicleRepository.save(new Vehicle("XYZ9K88", 12000.0, "diesel"));
		Operation emission = operation(vehicle, OptimizationCriterion.EMISSION, points);
		Operation distance = operation(other, OptimizationCriterion.DISTANCE, points);

		assertThatThrownBy(() -> service.compare(emission.getId(), distance.getId()))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("The routes were calculated for different vehicles");
	}

	@Test
	void refusesRoutesCalculatedForDifferentPoints() {
		Operation emission = operation(vehicle, OptimizationCriterion.EMISSION, points);
		Operation distance = operation(vehicle, OptimizationCriterion.DISTANCE, points.subList(0, 2));

		assertThatThrownBy(() -> service.compare(emission.getId(), distance.getId()))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessage("The routes were calculated for different delivery points");
	}

	@Test
	void refusesTheCriteriaInTheWrongPlaces() {
		Operation emission = operation(vehicle, OptimizationCriterion.EMISSION, points);
		Operation distance = operation(vehicle, OptimizationCriterion.DISTANCE, points);

		assertThatThrownBy(() -> service.compare(distance.getId(), emission.getId()))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("is not an emission route");
	}

	@Test
	void refusesTwoRoutesOfTheSameCriterion() {
		Operation first = operation(vehicle, OptimizationCriterion.EMISSION, points);
		Operation second = operation(vehicle, OptimizationCriterion.EMISSION, points);

		assertThatThrownBy(() -> service.compare(first.getId(), second.getId()))
			.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void failsForAnUnknownRoute() {
		Operation emission = operation(vehicle, OptimizationCriterion.EMISSION, points);

		assertThatThrownBy(() -> service.compare(emission.getId(), 9999L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("Route operation 9999 not found");
	}

}
