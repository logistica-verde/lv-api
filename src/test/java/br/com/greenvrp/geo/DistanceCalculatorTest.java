package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DistanceCalculatorTest {

	@Test
	void oneDegreeOfLongitudeAtEquatorIsAbout111Kilometers() {
		double meters = DistanceCalculator.haversineMeters(0.0, 0.0, 0.0, 1.0);

		assertThat(meters).isCloseTo(111_195.0, within(50.0));
	}

	@Test
	void distanceBetweenSamePointIsZero() {
		assertThat(DistanceCalculator.haversineMeters(-23.55, -46.63, -23.55, -46.63)).isZero();
	}

	@Test
	void distanceIsSymmetric() {
		double ab = DistanceCalculator.haversineMeters(-23.55, -46.63, -22.90, -43.17);
		double ba = DistanceCalculator.haversineMeters(-22.90, -43.17, -23.55, -46.63);

		assertThat(ab).isCloseTo(ba, within(1e-6));
	}

	@Test
	void matchesTheGeodesicDistanceSqlServerReportsBetweenPracaDaSeAndMasp() {
		double sqlServerGeographyMeters = 2_604.13;

		double meters = DistanceCalculator.haversineMeters(-23.5505, -46.6333, -23.5614, -46.6559);

		assertThat(meters).isCloseTo(sqlServerGeographyMeters, within(sqlServerGeographyMeters * 0.005));
	}

}
