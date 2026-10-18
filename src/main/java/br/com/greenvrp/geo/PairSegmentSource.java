package br.com.greenvrp.geo;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import java.util.List;

public interface PairSegmentSource {

	List<PairSegment> buildPairSegments(List<DeliveryPoint> points);

}
