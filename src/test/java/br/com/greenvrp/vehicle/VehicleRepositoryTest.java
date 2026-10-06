package br.com.greenvrp.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class VehicleRepositoryTest {

	@Autowired
	private VehicleRepository vehicleRepository;

	@Test
	void savesAndFindsActiveVehicle() {
		Vehicle vehicle = new Vehicle("ABC1D23", 7500.0, "diesel");

		vehicleRepository.save(vehicle);

		assertThat(vehicleRepository.findByAtivoTrue())
			.hasSize(1)
			.first()
			.satisfies(found -> {
				assertThat(found.getPlaca()).isEqualTo("ABC1D23");
				assertThat(found.getMassaKg()).isEqualTo(7500.0);
				assertThat(found.isAtivo()).isTrue();
			});
	}

	@Test
	void excludesInactiveVehiclesFromActiveQuery() {
		Vehicle vehicle = new Vehicle("XYZ9E88", 3000.0, "gnv");
		vehicle.desativar();

		vehicleRepository.save(vehicle);

		assertThat(vehicleRepository.findByAtivoTrue()).isEmpty();
	}

}
