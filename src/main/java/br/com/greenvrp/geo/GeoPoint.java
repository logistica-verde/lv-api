package br.com.greenvrp.geo;

public record GeoPoint(double latitude, double longitude) {

	private static final double GRID = 10_000.0;

	public GeoPoint snapped() {
		return new GeoPoint(Math.round(latitude * GRID) / GRID, Math.round(longitude * GRID) / GRID);
	}

}
