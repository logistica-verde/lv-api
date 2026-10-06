package br.com.greenvrp.geo;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GeoQueryService {

	private final SpatialRepository spatialRepository;

	public GeoQueryService(SpatialRepository spatialRepository) {
		this.spatialRepository = spatialRepository;
	}

	public List<PairSegment> buildPairSegments(List<DeliveryPoint> points) {
		List<RoadSegment> nearest = points.stream().map(this::nearestRoadSegment).toList();

		List<PairSegment> segments = new ArrayList<>();
		for (int from = 0; from < points.size(); from++) {
			for (int to = 0; to < points.size(); to++) {
				if (from == to) {
					continue;
				}
				DeliveryPoint a = points.get(from);
				DeliveryPoint b = points.get(to);
				RoadSegment roadA = nearest.get(from);
				RoadSegment roadB = nearest.get(to);
				segments.add(new PairSegment(
					from,
					to,
					DistanceCalculator.haversineMeters(
						a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude()),
					(roadA.getVelocidadeMedia() + roadB.getVelocidadeMedia()) / 2,
					(roadA.getDeclividade() + roadB.getDeclividade()) / 2));
			}
		}
		return segments;
	}

	private RoadSegment nearestRoadSegment(DeliveryPoint point) {
		return spatialRepository.findNearestSegment(point.getGeom().toText())
			.orElseThrow(() -> new RoadDataNotFoundException(
				"No road segment found for delivery point '%s'".formatted(point.getNome())));
	}

}
