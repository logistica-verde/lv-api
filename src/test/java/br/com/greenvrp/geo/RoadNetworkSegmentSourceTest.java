package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoadNetworkSegmentSourceTest {

	private static final GeoProperties PROPERTIES = new GeoProperties("osrm", 100, 2, 5, 90, 30);

	private final OsrmClient osrmClient = mock(OsrmClient.class);
	private final ElevationClient elevationClient = mock(ElevationClient.class);
	private final RoadNetworkSegmentSource source =
		new RoadNetworkSegmentSource(osrmClient, elevationClient, PROPERTIES, Runnable::run);

	private static final GeoPoint LOW_POINT = new GeoPoint(-23.560, -46.63);
	private static final GeoPoint HIGH_POINT = new GeoPoint(-23.551, -46.63);

	private final DeliveryPoint low = new DeliveryPoint("Baixo", null, -23.560, -46.63);
	private final DeliveryPoint high = new DeliveryPoint("Alto", null, -23.551, -46.63);

	@BeforeEach
	void climbingTowardsTheNorth() {
		when(elevationClient.elevations(anyList())).thenAnswer(invocation -> {
			List<GeoPoint> points = invocation.getArgument(0);
			Map<GeoPoint, Double> elevations = new HashMap<>();
			points.forEach(point -> elevations.put(point.snapped(), 700 + (point.latitude() + 23.56) * 11_100.0));
			return elevations;
		});
	}

	private static RoadPath northward(double durationSeconds) {
		return new RoadPath(1000, durationSeconds, List.of(new GeoPoint(-23.560, -46.63), new GeoPoint(-23.551, -46.63)));
	}

	private static RoadPath southward(double durationSeconds) {
		return new RoadPath(1000, durationSeconds, List.of(new GeoPoint(-23.551, -46.63), new GeoPoint(-23.560, -46.63)));
	}

	@Test
	void buildsOneSegmentPerOrderedPairOfPoints() {
		when(osrmClient.route(any(), any())).thenReturn(northward(100));

		List<PairSegment> segments = source.buildPairSegments(List.of(low, high));

		assertThat(segments).hasSize(2);
		assertThat(segments).extracting(PairSegment::fromIndex).containsExactly(0, 1);
		assertThat(segments).extracting(PairSegment::toIndex).containsExactly(1, 0);
	}

	@Test
	void usesTheDistanceOfTheRoadAndNotTheStraightLine() {
		when(osrmClient.route(any(), any())).thenReturn(northward(100));

		PairSegment first = source.buildPairSegments(List.of(low, high)).get(0);

		assertThat(first.distanceMeters()).isEqualTo(1000);
	}

	@Test
	void takesTheAverageSpeedFromTheDurationOfTheRoute() {
		when(osrmClient.route(any(), any())).thenReturn(northward(100));

		PairSegment first = source.buildPairSegments(List.of(low, high)).get(0);

		assertThat(first.averageSpeedKmh()).isCloseTo(36.0, within(1e-9));
	}

	@Test
	void keepsTheSpeedWithinWhatTheModelAccepts() {
		when(osrmClient.route(any(), any())).thenReturn(northward(10)).thenReturn(northward(100_000));

		List<PairSegment> segments = source.buildPairSegments(List.of(low, high));

		assertThat(segments.get(0).averageSpeedKmh()).isEqualTo(90);
		assertThat(segments.get(1).averageSpeedKmh()).isEqualTo(5);
	}

	@Test
	void fallsBackToTheDefaultSpeedWithoutADuration() {
		when(osrmClient.route(any(), any())).thenReturn(northward(0));

		PairSegment first = source.buildPairSegments(List.of(low, high)).get(0);

		assertThat(first.averageSpeedKmh()).isEqualTo(30);
	}

	@Test
	void climbsGoingNorthAndDescendsComingBack() {
		when(osrmClient.route(LOW_POINT, HIGH_POINT)).thenReturn(northward(100));
		when(osrmClient.route(HIGH_POINT, LOW_POINT)).thenReturn(southward(100));

		List<PairSegment> segments = source.buildPairSegments(List.of(low, high));

		assertThat(segments.get(0).slopePercent()).isGreaterThan(5);
		assertThat(segments.get(1).slopePercent()).isLessThan(-5);
		assertThat(segments.get(0).slopePercent()).isCloseTo(-segments.get(1).slopePercent(), within(1e-9));
	}

	@Test
	void isFlatWhenThereIsNoElevationData() {
		when(osrmClient.route(any(), any())).thenReturn(northward(100));
		when(elevationClient.elevations(anyList())).thenReturn(Map.of());

		PairSegment first = source.buildPairSegments(List.of(low, high)).get(0);

		assertThat(first.slopePercent()).isZero();
	}

	@Test
	void failsWhenTheRoutingServiceFails() {
		when(osrmClient.route(any(), any())).thenThrow(new RoutingUnavailableException("down", null));

		assertThatThrownBy(() -> source.buildPairSegments(List.of(low, high)))
			.isInstanceOf(RoutingUnavailableException.class);
	}

	@Test
	void failsWhenTwoPointsHaveNoRoadBetweenThem() {
		when(osrmClient.route(any(), any())).thenThrow(new RoadDataNotFoundException("no road"));

		assertThatThrownBy(() -> source.buildPairSegments(List.of(low, high)))
			.isInstanceOf(RoadDataNotFoundException.class);
	}

}
