package br.com.greenvrp.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.geo.CoverageArea;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({RouteService.class, CoverageArea.class})
class RouteServiceTest {

	@Autowired
	private RouteService routeService;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	@MockitoBean
	private RouteProcessor routeProcessor;

	private Vehicle vehicle;
	private DeliveryPoint first;
	private DeliveryPoint second;

	@BeforeEach
	void setUp() {
		vehicle = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		first = deliveryPointRepository.save(new DeliveryPoint("A", null, -23.55, -46.63));
		second = deliveryPointRepository.save(new DeliveryPoint("B", null, -23.56, -46.64));
	}

	private RouteRequest request(Long vehicleId, Long... pointIds) {
		return new RouteRequest(vehicleId, List.of(pointIds), OptimizationCriterion.EMISSION);
	}

	@Test
	void calculateCreatesPendingOperationAndStartsProcessing() {
		RouteAcceptedResponse accepted = routeService.calculate(request(vehicle.getId(), first.getId(), second.getId()));

		assertThat(accepted.status()).isEqualTo(OperationStatus.PENDING);
		verify(routeProcessor).processAsync(accepted.id());

		RouteResponse stored = routeService.get(accepted.id());
		assertThat(stored.status()).isEqualTo(OperationStatus.PENDING);
		assertThat(stored.vehicleId()).isEqualTo(vehicle.getId());
		assertThat(stored.criterion()).isEqualTo(OptimizationCriterion.EMISSION);
		assertThat(stored.points()).extracting(RouteResponse.PointItem::name).containsExactly("A", "B");
		assertThat(stored.segments()).isEmpty();
		assertThat(stored.totalDistanceMeters()).isNull();
	}

	@Test
	void calculateRejectsUnknownVehicle() {
		assertThatThrownBy(() -> routeService.calculate(request(999L, first.getId(), second.getId())))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining("Vehicle 999");
		verifyNoInteractions(routeProcessor);
	}

	@Test
	void calculateRejectsInactiveVehicle() {
		Vehicle inactive = new Vehicle("OFF0000", 3000.0, "diesel");
		inactive.desativar();
		Vehicle saved = vehicleRepository.save(inactive);

		assertThatThrownBy(() -> routeService.calculate(request(saved.getId(), first.getId(), second.getId())))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("inactive");
	}

	@Test
	void calculateRejectsUnknownDeliveryPoint() {
		assertThatThrownBy(() -> routeService.calculate(request(vehicle.getId(), first.getId(), 999L)))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining("Delivery point 999");
	}

	@Test
	void calculateRejectsInactiveDeliveryPoint() {
		DeliveryPoint inactive = new DeliveryPoint("Off", null, -23.0, -46.0);
		inactive.desativar();
		DeliveryPoint saved = deliveryPointRepository.save(inactive);

		assertThatThrownBy(() -> routeService.calculate(request(vehicle.getId(), first.getId(), saved.getId())))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("inactive");
	}

	@Test
	void calculateRejectsDuplicateDeliveryPoints() {
		assertThatThrownBy(() -> routeService.calculate(request(vehicle.getId(), first.getId(), first.getId())))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("duplicates");
	}

	@Test
	void getRejectsUnknownOperation() {
		assertThatThrownBy(() -> routeService.get(12345L))
			.isInstanceOf(ResourceNotFoundException.class);
	}

}
