package br.com.greenvrp.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VehicleImageTest {

	@ParameterizedTest
	@EnumSource(VehicleImage.class)
	void roundTripsThroughItsLowerCaseKey(VehicleImage image) {
		assertThat(image.key()).isEqualTo(image.name().toLowerCase());
		assertThat(VehicleImage.fromKey(image.key())).isEqualTo(image);
	}

	@Test
	void offersTheSixImagesTheFrontKnows() {
		assertThat(VehicleImage.values())
			.extracting(VehicleImage::key)
			.containsExactly("van", "pickup", "vuc", "truck", "semi", "motorcycle");
	}

	@Test
	void usesTheTruckByDefault() {
		assertThat(VehicleImage.DEFAULT).isEqualTo(VehicleImage.TRUCK);
		assertThat(new Vehicle("ABC1D23", 7500.0, "diesel").getImagem()).isEqualTo(VehicleImage.TRUCK);
		assertThat(new Vehicle("ABC1D23", 7500.0, "diesel", null).getImagem()).isEqualTo(VehicleImage.TRUCK);
	}

	@Test
	void rejectsAnUnknownKey() {
		assertThatThrownBy(() -> VehicleImage.fromKey("helicopter"))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
