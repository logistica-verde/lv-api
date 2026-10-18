package br.com.greenvrp.geo;

import java.util.List;

public record RoadPath(double distanceMeters, double durationSeconds, List<GeoPoint> geometry) {
}
