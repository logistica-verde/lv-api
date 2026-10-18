package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlopeCalculatorTest {

	private static final double STEP = 100.0;

	@Test
	void samplesTheRouteEveryStepKeepingBothEnds() {
		List<GeoPoint> path = List.of(new GeoPoint(0, 0), new GeoPoint(0.01, 0));

		List<GeoPoint> sampled = SlopeCalculator.resample(path, STEP);

		assertThat(sampled.get(0)).isEqualTo(path.get(0));
		assertThat(sampled.get(sampled.size() - 1)).isEqualTo(path.get(1));
		assertThat(sampled).hasSize(13);
	}

	@Test
	void samplesAcrossTheBendsOfTheGeometry() {
		List<GeoPoint> path = List.of(new GeoPoint(0, 0), new GeoPoint(0.005, 0), new GeoPoint(0.005, 0.005));

		List<GeoPoint> sampled = SlopeCalculator.resample(path, STEP);

		assertThat(sampled).hasSizeBetween(10, 13);
		assertThat(sampled).contains(path.get(0), path.get(2));
	}

	@Test
	void keepsOnlyTheEndsOfAShortRoute() {
		List<GeoPoint> path = List.of(new GeoPoint(0, 0), new GeoPoint(0.0003, 0));

		assertThat(SlopeCalculator.resample(path, STEP)).containsExactly(path.get(0), path.get(1));
	}

	@Test
	void returnsWhatItGotWhenThereIsNoLine() {
		assertThat(SlopeCalculator.resample(List.of(new GeoPoint(1, 1)), STEP)).hasSize(1);
		assertThat(SlopeCalculator.resample(List.of(), STEP)).isEmpty();
	}

	@Test
	void isPositiveWhenTheRoadClimbs() {
		double slope = SlopeCalculator.slopePercent(List.of(0.0, 10.0, 20.0, 30.0), 300);

		assertThat(slope).isCloseTo(10.0, within(1e-9));
	}

	@Test
	void isNegativeWhenTheRoadDescends() {
		double slope = SlopeCalculator.slopePercent(List.of(30.0, 20.0, 10.0, 0.0), 300);

		assertThat(slope).isCloseTo(-10.0, within(1e-9));
	}

	@Test
	void isDirectional() {
		List<Double> up = List.of(0.0, 5.0, 10.0, 15.0, 20.0, 25.0);
		List<Double> down = List.of(25.0, 20.0, 15.0, 10.0, 5.0, 0.0);

		assertThat(SlopeCalculator.slopePercent(up, 500))
			.isCloseTo(-SlopeCalculator.slopePercent(down, 500), within(1e-9));
	}

	@Test
	void smoothsTheNoiseAtTheEnds() {
		List<Double> noisy = List.of(0.0, 6.0, 3.0, 12.0, 15.0, 24.0, 27.0, 30.0, 33.0);

		double slope = SlopeCalculator.slopePercent(noisy, 800);

		assertThat(slope).isBetween(3.0, 5.0);
	}

	@Test
	void neverExceedsWhatARoadCanHave() {
		assertThat(SlopeCalculator.slopePercent(List.of(0.0, 100.0), 100))
			.isEqualTo(SlopeCalculator.MAX_GRADE_PERCENT);
		assertThat(SlopeCalculator.slopePercent(List.of(100.0, 0.0), 100))
			.isEqualTo(-SlopeCalculator.MAX_GRADE_PERCENT);
	}

	@Test
	void isFlatWhenTheRouteIsTooShortToMeasure() {
		assertThat(SlopeCalculator.slopePercent(List.of(0.0, 5.0), 30)).isZero();
	}

	@Test
	void fillsTheGapsWithTheLastKnownElevation() {
		List<Double> withGaps = Arrays.asList(null, 10.0, null, 30.0);

		double slope = SlopeCalculator.slopePercent(withGaps, 300);

		assertThat(slope).isCloseTo(20.0 / 300 * 100, within(1e-9));
	}

	@Test
	void isFlatWithoutAnyElevation() {
		assertThat(SlopeCalculator.slopePercent(Arrays.asList(null, null, null), 300)).isZero();
		assertThat(SlopeCalculator.slopePercent(List.of(), 300)).isZero();
	}

}
