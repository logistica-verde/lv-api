package br.com.greenvrp.geo;

public record PairSegment(

	int fromIndex,
	int toIndex,
	double distanceMeters,
	double averageSpeedKmh,
	double slopePercent

) {
}
