package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

class GeoQueryServiceTest {

	private final GeometryFactory factory = new GeometryFactory();
	private final SpatialRepository spatialRepository = mock(SpatialRepository.class);
	private final GeoQueryService service = new GeoQueryService(spatialRepository);

	private RoadSegment road(double speed, double slope) {
		LineString line = factory.createLineString(
			new Coordinate[] {new Coordinate(0, 0), new Coordinate(0.001, 0.001)});
		return new RoadSegment(line, speed, slope, 150.0);
	}

	@Test
	void buildsOneSegmentPerOrderedPairOfPoints() {
		when(spatialRepository.findNearestSegment(anyString())).thenReturn(Optional.of(road(40.0, 2.0)));
		List<DeliveryPoint> points = List.of(
			new DeliveryPoint("A", null, -23.55, -46.63),
			new DeliveryPoint("B", null, -23.56, -46.64),
			new DeliveryPoint("C", null, -23.57, -46.65));

		List<PairSegment> segments = service.buildPairSegments(points);

		assertThat(segments).hasSize(6);
		assertThat(segments).noneMatch(s -> s.fromIndex() == s.toIndex());
		assertThat(segments).allMatch(s -> s.distanceMeters() > 0);
	}

	@Test
	void averagesSpeedAndSlopeOfTheNearestRoadSegments() {
		DeliveryPoint a = new DeliveryPoint("A", null, -23.55, -46.63);
		DeliveryPoint b = new DeliveryPoint("B", null, -23.56, -46.64);
		when(spatialRepository.findNearestSegment(a.getGeom().toText()))
			.thenReturn(Optional.of(road(30.0, 1.0)));
		when(spatialRepository.findNearestSegment(b.getGeom().toText()))
			.thenReturn(Optional.of(road(50.0, 3.0)));

		PairSegment first = service.buildPairSegments(List.of(a, b)).get(0);

		assertThat(first.fromIndex()).isZero();
		assertThat(first.toIndex()).isEqualTo(1);
		assertThat(first.averageSpeedKmh()).isCloseTo(40.0, within(1e-9));
		assertThat(first.slopePercent()).isCloseTo(2.0, within(1e-9));
	}

	@Test
	void failsWhenAPointHasNoRoadSegment() {
		when(spatialRepository.findNearestSegment(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.buildPairSegments(
				List.of(new DeliveryPoint("Isolado", null, -23.55, -46.63))))
			.isInstanceOf(RoadDataNotFoundException.class)
			.hasMessageContaining("Isolado");
	}

}
