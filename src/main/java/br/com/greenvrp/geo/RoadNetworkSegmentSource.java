package br.com.greenvrp.geo;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "lv.geo", name = "provider", havingValue = "osrm")
public class RoadNetworkSegmentSource implements PairSegmentSource {

	private record Leg(int from, int to) {
	}

	private record Traced(Leg leg, RoadPath path, List<GeoPoint> samples) {
	}

	private final OsrmClient osrmClient;
	private final ElevationClient elevationClient;
	private final GeoProperties properties;
	private final Executor executor;

	public RoadNetworkSegmentSource(
		OsrmClient osrmClient,
		ElevationClient elevationClient,
		GeoProperties properties,
		@Qualifier("geoExecutor") Executor executor) {
		this.osrmClient = osrmClient;
		this.elevationClient = elevationClient;
		this.properties = properties;
		this.executor = executor;
	}

	@Override
	public List<PairSegment> buildPairSegments(List<DeliveryPoint> points) {
		return buildPairSegments(points, (done, total) -> { });
	}

	@Override
	public List<PairSegment> buildPairSegments(List<DeliveryPoint> points, TracingListener listener) {
		int legs = points.size() * (points.size() - 1);
		int steps = legs + 1;
		List<Traced> traced = trace(points, listener, steps);
		List<GeoPoint> everySample = traced.stream().flatMap(item -> item.samples().stream()).toList();
		Map<GeoPoint, Double> elevations = elevationClient.elevations(everySample);
		listener.traced(steps, steps);
		return traced.stream().map(item -> toSegment(item, elevations)).toList();
	}

	private List<Traced> trace(List<DeliveryPoint> points, TracingListener listener, int steps) {
		AtomicInteger done = new AtomicInteger();
		List<CompletableFuture<Traced>> pending = new ArrayList<>();
		for (int from = 0; from < points.size(); from++) {
			for (int to = 0; to < points.size(); to++) {
				if (from != to) {
					Leg leg = new Leg(from, to);
					GeoPoint start = pointOf(points.get(from));
					GeoPoint end = pointOf(points.get(to));
					pending.add(CompletableFuture.supplyAsync(() -> trace(leg, start, end), executor)
						.whenComplete((result, failure) -> {
							if (failure == null) {
								listener.traced(done.incrementAndGet(), steps);
							}
						}));
				}
			}
		}
		try {
			return pending.stream().map(CompletableFuture::join).toList();
		}
		catch (CompletionException failure) {
			if (failure.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw failure;
		}
	}

	private Traced trace(Leg leg, GeoPoint start, GeoPoint end) {
		RoadPath path = osrmClient.route(start, end);
		List<GeoPoint> samples = SlopeCalculator.resample(path.geometry(), properties.sampleStepMeters());
		return new Traced(leg, path, samples);
	}

	private PairSegment toSegment(Traced item, Map<GeoPoint, Double> elevations) {
		List<Double> profile = item.samples().stream().map(sample -> elevations.get(sample.snapped())).toList();
		return new PairSegment(
			item.leg().from(),
			item.leg().to(),
			item.path().distanceMeters(),
			speedOf(item.path()),
			SlopeCalculator.slopePercent(profile, item.path().distanceMeters()),
			item.path().geometry());
	}

	private double speedOf(RoadPath path) {
		if (path.durationSeconds() <= 0) {
			return properties.fallbackSpeedKmh();
		}
		double kmh = path.distanceMeters() / path.durationSeconds() * 3.6;
		return Math.max(properties.minSpeedKmh(), Math.min(properties.maxSpeedKmh(), kmh));
	}

	private static GeoPoint pointOf(DeliveryPoint point) {
		return new GeoPoint(point.getLatitude(), point.getLongitude());
	}

}
