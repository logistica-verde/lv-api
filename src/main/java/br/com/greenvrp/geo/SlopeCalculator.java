package br.com.greenvrp.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SlopeCalculator {

	public static final double MAX_GRADE_PERCENT = 15.0;
	public static final double MIN_LENGTH_METERS = 50.0;
	private static final int SMOOTHING_WINDOW = 3;

	private SlopeCalculator() {
	}

	public static List<GeoPoint> resample(List<GeoPoint> path, double stepMeters) {
		if (path.size() < 2) {
			return List.copyOf(path);
		}
		List<GeoPoint> sampled = new ArrayList<>();
		sampled.add(path.get(0));
		double nextAt = stepMeters;
		double walked = 0;
		for (int index = 1; index < path.size(); index++) {
			GeoPoint start = path.get(index - 1);
			GeoPoint end = path.get(index);
			double length = distance(start, end);
			while (length > 0 && walked + length >= nextAt) {
				double fraction = (nextAt - walked) / length;
				sampled.add(new GeoPoint(
					start.latitude() + (end.latitude() - start.latitude()) * fraction,
					start.longitude() + (end.longitude() - start.longitude()) * fraction));
				nextAt += stepMeters;
			}
			walked += length;
		}
		GeoPoint last = path.get(path.size() - 1);
		if (distance(sampled.get(sampled.size() - 1), last) > 1.0) {
			sampled.add(last);
		}
		return sampled;
	}

	public static double slopePercent(List<Double> elevations, double lengthMeters) {
		List<Double> filled = fillGaps(elevations);
		if (filled.size() < 2 || lengthMeters < MIN_LENGTH_METERS) {
			return 0.0;
		}
		int count = filled.size();
		int window = Math.max(1, Math.min(SMOOTHING_WINDOW, count / 3));
		double start = average(filled.subList(0, window));
		double end = average(filled.subList(count - window, count));
		double effectiveLength = lengthMeters * (count - window) / (count - 1);
		double grade = (end - start) / effectiveLength * 100.0;
		return Math.max(-MAX_GRADE_PERCENT, Math.min(MAX_GRADE_PERCENT, grade));
	}

	private static List<Double> fillGaps(List<Double> elevations) {
		Double firstKnown = elevations.stream().filter(Objects::nonNull).findFirst().orElse(null);
		if (firstKnown == null) {
			return List.of();
		}
		List<Double> filled = new ArrayList<>();
		double previous = firstKnown;
		for (Double value : elevations) {
			previous = value == null ? previous : value;
			filled.add(previous);
		}
		return filled;
	}

	private static double average(List<Double> values) {
		return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
	}

	private static double distance(GeoPoint first, GeoPoint second) {
		return DistanceCalculator.haversineMeters(
			first.latitude(), first.longitude(), second.latitude(), second.longitude());
	}

}
