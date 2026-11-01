package br.com.greenvrp.geo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpatialRepository extends JpaRepository<RoadSegment, Long> {

	@Query(
		value = "SELECT TOP 1 * FROM malha_viaria "
			+ "ORDER BY geography::STGeomFromWKB(geom.STAsBinary(), 4326)"
			+ ".STDistance(geography::STGeomFromText(:wkt, 4326)) ASC",
		nativeQuery = true)
	Optional<RoadSegment> findNearestSegment(@Param("wkt") String wkt);

}
