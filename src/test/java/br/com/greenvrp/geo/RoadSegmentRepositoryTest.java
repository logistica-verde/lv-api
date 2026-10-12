package br.com.greenvrp.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class RoadSegmentRepositoryTest {

	@Autowired
	private SpatialRepository spatialRepository;

	@Test
	void persistsAndReloadsRoadSegmentWithLineGeometry() {
		LineString line = new GeometryFactory().createLineString(
			new Coordinate[] {new Coordinate(-46.63, -23.55), new Coordinate(-46.62, -23.54)});
		RoadSegment saved = spatialRepository.saveAndFlush(new RoadSegment(line, 35.0, 2.5, 1400.0));

		RoadSegment reloaded = spatialRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getGeom().getNumPoints()).isEqualTo(2);
		assertThat(reloaded.getVelocidadeMedia()).isEqualTo(35.0);
		assertThat(reloaded.getDeclividade()).isEqualTo(2.5);
		assertThat(reloaded.getDistanciaMetros()).isEqualTo(1400.0);
	}

}
