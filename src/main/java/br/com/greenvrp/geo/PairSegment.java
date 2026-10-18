package br.com.greenvrp.geo;

import java.util.List;

public record PairSegment(

	int fromIndex,
	int toIndex,
	double distanceMeters,
	double averageSpeedKmh,
	double slopePercent,
	List<GeoPoint> geometry

) {

	public PairSegment(int fromIndex, int toIndex, double distanceMeters, double averageSpeedKmh, double slopePercent) {
		this(fromIndex, toIndex, distanceMeters, averageSpeedKmh, slopePercent, null);
	}

}
