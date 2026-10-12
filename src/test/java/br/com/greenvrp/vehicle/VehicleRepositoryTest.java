package br.com.greenvrp.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

	@Test
	void persistsTheChosenImageAndDefaultsToTheTruck() {
		vehicleRepository.save(new Vehicle("VAN1A11", 1800.0, "etanol", VehicleImage.VAN));
		vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));

		assertThat(vehicleRepository.findAllByOrderByIdAsc())
			.extracting(Vehicle::getPlaca, Vehicle::getImagem)
			.containsExactly(
				tuple("VAN1A11", VehicleImage.VAN),
				tuple("ABC1D23", VehicleImage.TRUCK));
	}

	@Test
	void listsEveryVehicleInOrderIncludingInactiveOnes() {
		Vehicle inactive = new Vehicle("OLD0A00", 3000.0, "gnv");
		inactive.desativar();
		vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		vehicleRepository.save(inactive);

		assertThat(vehicleRepository.findAllByOrderByIdAsc())
			.extracting(Vehicle::getPlaca)
			.containsExactly("ABC1D23", "OLD0A00");
	}

	@Test
	void reactivatesAVehicle() {
		Vehicle vehicle = new Vehicle("XYZ9E88", 3000.0, "gnv");
		vehicle.desativar();
		Vehicle saved = vehicleRepository.save(vehicle);

		saved.ativar();
		vehicleRepository.saveAndFlush(saved);

		assertThat(vehicleRepository.findByAtivoTrue()).hasSize(1);
	}

}
