package br.com.greenvrp.route;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class OperationRepositoryTest {

	@Autowired
	private OperationRepository operationRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void persistsPendingOperationWithPointsInRequestedOrder() {
		Vehicle vehicle = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		DeliveryPoint first = deliveryPointRepository.save(new DeliveryPoint("A", null, -23.55, -46.63));
		DeliveryPoint second = deliveryPointRepository.save(new DeliveryPoint("B", null, -23.56, -46.64));

		Operation saved = operationRepository.saveAndFlush(
			Operation.pending(vehicle, OptimizationCriterion.EMISSION, List.of(second, first)));
		entityManager.clear();

		Operation reloaded = operationRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getStatus()).isEqualTo(OperationStatus.PENDING);
		assertThat(reloaded.getCriterio()).isEqualTo(OptimizationCriterion.EMISSION);
		assertThat(reloaded.getPoints())
			.extracting(point -> point.getPoint().getNome(), OperationPoint::getOrdemVisita)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple("B", 1),
				org.assertj.core.groups.Tuple.tuple("A", 2));
	}

}
