package br.com.greenvrp.experiment;

import br.com.greenvrp.geo.PairSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class InstanceGenerator {

	private static final double MIN_LATITUDE = -23.65;
	private static final double MAX_LATITUDE = -23.45;
	private static final double MIN_LONGITUDE = -46.75;
	private static final double MAX_LONGITUDE = -46.55;
	private static final double EARTH_RADIUS_METERS = 6_371_000.0;
	private static final double ROAD_CIRCUITY = 1.3;
	private static final double MIN_SPEED_KMH = 25.0;
	private static final double SPEED_RANGE_KMH = 35.0;
	private static final double SLOPE_RANGE_PERCENT = 6.0;

	private InstanceGenerator() {
	}

	static List<PairSegment> pairSegments(int size, int seed) {
		Random random = new Random(31L * size + 1_000_003L * seed);
		double[] latitudes = new double[size];
		double[] longitudes = new double[size];
		for (int index = 0; index < size; index++) {
			latitudes[index] = MIN_LATITUDE + random.nextDouble() * (MAX_LATITUDE - MIN_LATITUDE);
			longitudes[index] = MIN_LONGITUDE + random.nextDouble() * (MAX_LONGITUDE - MIN_LONGITUDE);
		}
		List<PairSegment> pairs = new ArrayList<>();
		for (int from = 0; from < size; from++) {
			for (int to = 0; to < size; to++) {
				if (from == to) {
					continue;
				}
				double distance = haversineMeters(latitudes[from], longitudes[from], latitudes[to], longitudes[to])
					* ROAD_CIRCUITY;
				double speed = MIN_SPEED_KMH + random.nextDouble() * SPEED_RANGE_KMH;
				double slope = (random.nextDouble() - 0.5) * SLOPE_RANGE_PERCENT;
				pairs.add(new PairSegment(from, to, distance, speed, slope));
			}
		}
		return pairs;
	}

	private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
		double deltaLatitude = Math.toRadians(lat2 - lat1);
		double deltaLongitude = Math.toRadians(lon2 - lon1);
		double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
			+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
		return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
	}

}
