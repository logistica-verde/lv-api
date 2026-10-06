package br.com.greenvrp.deliverypoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class DeliveryPointRepositoryTest {

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	@Test
	void persistsAndReloadsPointCoordinates() {
		DeliveryPoint saved = deliveryPointRepository.saveAndFlush(
			new DeliveryPoint("Centro", "Praca da Se", -23.5505, -46.6333));

		DeliveryPoint reloaded = deliveryPointRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getLatitude()).isCloseTo(-23.5505, within(1e-6));
		assertThat(reloaded.getLongitude()).isCloseTo(-46.6333, within(1e-6));
	}

	@Test
	void excludesInactivePointsFromActiveQuery() {
		DeliveryPoint inactive = new DeliveryPoint("Antigo", null, -23.0, -46.0);
		inactive.desativar();
		deliveryPointRepository.save(inactive);
		deliveryPointRepository.save(new DeliveryPoint("Ativo", null, -23.1, -46.1));

		assertThat(deliveryPointRepository.findByAtivoTrue())
			.extracting(DeliveryPoint::getNome)
			.containsExactly("Ativo");
	}

}
