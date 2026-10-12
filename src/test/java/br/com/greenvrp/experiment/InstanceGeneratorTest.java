package br.com.greenvrp.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.greenvrp.geo.PairSegment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstanceGeneratorTest {

	@ParameterizedTest
	@ValueSource(ints = {2, 10, 30})
	void createsOnePairForEveryOrderedCombinationOfPoints(int size) {
		List<PairSegment> pairs = InstanceGenerator.pairSegments(size, 1);

		assertThat(pairs).hasSize(size * (size - 1));
		assertThat(pairs).allSatisfy(pair -> {
			assertThat(pair.fromIndex()).isBetween(0, size - 1);
			assertThat(pair.toIndex()).isBetween(0, size - 1);
			assertThat(pair.fromIndex()).isNotEqualTo(pair.toIndex());
		});
		assertThat(pairs.stream().map(pair -> pair.fromIndex() + ":" + pair.toIndex()).distinct()).hasSize(pairs.size());
	}

	@Test
	void producesPlausibleRoadFigures() {
		List<PairSegment> pairs = InstanceGenerator.pairSegments(15, 3);

		assertThat(pairs).allSatisfy(pair -> {
			assertThat(pair.distanceMeters()).isBetween(1.0, 60_000.0);
			assertThat(pair.averageSpeedKmh()).isBetween(25.0, 60.0);
			assertThat(pair.slopePercent()).isBetween(-3.0, 3.0);
		});
	}

	@Test
	void givesTheSameInstanceForTheSameSizeAndSeed() {
		assertThat(InstanceGenerator.pairSegments(12, 7)).isEqualTo(InstanceGenerator.pairSegments(12, 7));
	}

	@Test
	void givesDifferentInstancesForDifferentSeedsOrSizes() {
		assertThat(InstanceGenerator.pairSegments(12, 1)).isNotEqualTo(InstanceGenerator.pairSegments(12, 2));
		assertThat(InstanceGenerator.pairSegments(12, 1).get(0))
			.isNotEqualTo(InstanceGenerator.pairSegments(13, 1).get(0));
	}

}
