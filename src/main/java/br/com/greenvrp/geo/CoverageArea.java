package br.com.greenvrp.geo;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CoverageArea {

	private static final int VALUES = 4;

	private final double[] bounds;

	public CoverageArea(@Value("${lv.geo.coverage:}") String coverage) {
		this.bounds = parse(coverage);
	}

	public boolean isDefined() {
		return bounds.length == VALUES;
	}

	public boolean contains(double latitude, double longitude) {
		return !isDefined()
			|| (latitude >= bounds[0] && latitude <= bounds[2] && longitude >= bounds[1] && longitude <= bounds[3]);
	}

	private static double[] parse(String coverage) {
		if (coverage == null || coverage.isBlank()) {
			return new double[0];
		}
		double[] values = Arrays.stream(coverage.split(",")).map(String::trim).mapToDouble(Double::parseDouble).toArray();
		if (values.length != VALUES || values[0] >= values[2] || values[1] >= values[3]) {
			throw new IllegalArgumentException(
				"lv.geo.coverage must be minLatitude,minLongitude,maxLatitude,maxLongitude: " + coverage);
		}
		return values;
	}

}
