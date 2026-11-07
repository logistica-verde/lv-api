package br.com.greenvrp.geo;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import java.util.List;

public interface PairSegmentSource {

	@FunctionalInterface
	interface TracingListener {

		void traced(int done, int total);

	}

	List<PairSegment> buildPairSegments(List<DeliveryPoint> points);

	default List<PairSegment> buildPairSegments(List<DeliveryPoint> points, TracingListener listener) {
		List<PairSegment> segments = buildPairSegments(points);
		listener.traced(1, 1);
		return segments;
	}

}
