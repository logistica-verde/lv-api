package br.com.greenvrp.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.greenvrp.api.InvalidRequestException;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({RouteService.class, CoverageArea.class})
@TestPropertySource(properties = "lv.geo.coverage=-24.10,-46.95,-23.25,-46.25")
class RouteServiceOriginTest {

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

	private RouteRequest from(double latitude, double longitude, Long... pointIds) {
		return new RouteRequest(
			vehicle.getId(), List.of(pointIds), OptimizationCriterion.EMISSION,
			new RouteRequest.Origin(latitude, longitude));
	}

	private List<String> namesOf(RouteAcceptedResponse accepted) {
		return routeService.get(accepted.id()).points().stream().map(RouteResponse.PointItem::name).toList();
	}

	private List<Long> idsOf(RouteAcceptedResponse accepted) {
		return routeService.get(accepted.id()).points().stream().map(RouteResponse.PointItem::pointId).toList();
	}

	@Test
	void startsTheRouteAtTheOriginAndKeepsTheStopsInAFixedOrder() {
		RouteAcceptedResponse accepted = routeService.calculate(
			from(-23.5505, -46.6333, second.getId(), first.getId()));

		assertThat(namesOf(accepted)).containsExactly("Minha localização", "A", "B");
	}

	@Test
	void theOrderOfSelectionDoesNotChangeTheOperation() {
		RouteAcceptedResponse ascending = routeService.calculate(
			from(-23.5505, -46.6333, first.getId(), second.getId()));
		RouteAcceptedResponse descending = routeService.calculate(
			from(-23.5505, -46.6333, second.getId(), first.getId()));

		assertThat(idsOf(ascending)).isEqualTo(idsOf(descending));
	}

	@Test
	void keepsTheOriginOutOfTheListOfDeliveryPoints() {
		routeService.calculate(from(-23.5505, -46.6333, first.getId(), second.getId()));

		assertThat(deliveryPointRepository.findByAtivoTrue()).extracting(DeliveryPoint::getNome)
			.containsExactlyInAnyOrder("A", "B");
	}

	@Test
	void reusesTheOriginPointForTheSameLocation() {
		RouteAcceptedResponse emission = routeService.calculate(from(-23.5505, -46.6333, first.getId()));
		RouteAcceptedResponse distance = routeService.calculate(from(-23.55051, -46.63332, first.getId()));

		assertThat(idsOf(emission).get(0)).isEqualTo(idsOf(distance).get(0));
	}

	@Test
	void createsAnotherPointForAnotherLocation() {
		RouteAcceptedResponse near = routeService.calculate(from(-23.5505, -46.6333, first.getId()));
		RouteAcceptedResponse far = routeService.calculate(from(-23.5614, -46.6559, first.getId()));

		assertThat(idsOf(near).get(0)).isNotEqualTo(idsOf(far).get(0));
	}

	@Test
	void acceptsASingleStopWhenThereIsAnOrigin() {
		RouteAcceptedResponse accepted = routeService.calculate(from(-23.5505, -46.6333, first.getId()));

		assertThat(namesOf(accepted)).containsExactly("Minha localização", "A");
	}

	@Test
	void refusesAnOriginOutsideTheCoveredArea() {
		assertThatThrownBy(() -> routeService.calculate(from(-22.9068, -43.1729, first.getId())))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("outside the area");
		verifyNoInteractions(routeProcessor);
	}

	@Test
	void stillRefusesASingleStopWithoutAnOrigin() {
		RouteRequest alone = new RouteRequest(vehicle.getId(), List.of(first.getId()), OptimizationCriterion.EMISSION);

		assertThatThrownBy(() -> routeService.calculate(alone)).isInstanceOf(InvalidRequestException.class);
	}

}
