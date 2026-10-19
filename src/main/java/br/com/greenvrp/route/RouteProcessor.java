package br.com.greenvrp.route;

import br.com.greenvrp.blockchain.BlockchainService;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.geo.PairSegmentSource;
import br.com.greenvrp.geo.PairSegment;
import br.com.greenvrp.optimizer.InvalidOptimizerResponseException;
import br.com.greenvrp.optimizer.OptimizeRequest;
import br.com.greenvrp.optimizer.OptimizeResponse;
import br.com.greenvrp.optimizer.OptimizerClient;
import br.com.greenvrp.settings.GaSettingsService;
import br.com.greenvrp.settings.GaSettingsValues;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RouteProcessor {

	private static final Logger log = LoggerFactory.getLogger(RouteProcessor.class);
	private static final int MAX_ERROR_LENGTH = 500;
	private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

	private record Prepared(OptimizeRequest request, List<PairSegment> pairs) {
	}

	private record Leg(int from, int to) {
	}

	private final OperationRepository operationRepository;
	private final PairSegmentSource pairSegmentSource;
	private final OptimizerClient optimizerClient;
	private final BlockchainService blockchainService;
	private final GaSettingsService gaSettingsService;
	private final TransactionTemplate transactions;
	private final Executor executor;

	public RouteProcessor(
		OperationRepository operationRepository,
		PairSegmentSource pairSegmentSource,
		OptimizerClient optimizerClient,
		BlockchainService blockchainService,
		GaSettingsService gaSettingsService,
		PlatformTransactionManager transactionManager,
		@Qualifier("routeExecutor") Executor executor) {
		this.operationRepository = operationRepository;
		this.pairSegmentSource = pairSegmentSource;
		this.optimizerClient = optimizerClient;
		this.blockchainService = blockchainService;
		this.gaSettingsService = gaSettingsService;
		this.transactions = new TransactionTemplate(transactionManager);
		this.executor = executor;
	}

	public CompletableFuture<Void> processAsync(Long operationId) {
		return CompletableFuture.supplyAsync(() -> prepare(operationId), executor)
			.thenCompose(prepared -> {
				long startedAt = System.nanoTime();
				return optimizerClient.optimize(prepared.request())
					.thenAcceptAsync(response -> complete(operationId, prepared, response, elapsedMillis(startedAt)), executor);
			})
			.thenApply(done -> true)
			.exceptionally(error -> {
				markFailed(operationId, error);
				return false;
			})
			.thenAcceptAsync(completed -> {
				if (completed) {
					blockchainService.anchorOperation(operationId);
				}
			}, executor);
	}

	private Prepared prepare(Long operationId) {
		return transactions.execute(status -> {
			Operation operation = loadOperation(operationId);
			List<DeliveryPoint> points = pointsOf(operation);
			List<PairSegment> pairs = pairSegmentSource.buildPairSegments(points);
			GaSettingsValues settings = gaSettingsService.current();
			OptimizeRequest.GaParams gaParams = new OptimizeRequest.GaParams(
				settings.populationSize(), settings.generations(), settings.crossoverRate(), settings.mutationRate());
			OptimizeRequest request = OptimizeRequest.of(
				operation.getCriterio(), operation.getMassaTotalKg(), points.size(), pairs, gaParams,
				seedOf(points.stream().map(DeliveryPoint::getId).toList()));
			return new Prepared(request, pairs);
		});
	}

	static long seedOf(List<Long> pointIds) {
		long seed = 17;
		for (Long id : pointIds) {
			seed = seed * 31 + id;
		}
		return seed & Long.MAX_VALUE;
	}

	private static long elapsedMillis(long startedAtNanos) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
	}

	private void complete(Long operationId, Prepared prepared, OptimizeResponse response, long executionMillis) {
		transactions.executeWithoutResult(status -> {
			Operation operation = loadOperation(operationId);
			List<DeliveryPoint> points = pointsOf(operation);
			List<Integer> sequence = response.sequence();
			validateSequence(sequence, points.size());

			List<RouteSegment> legs = buildLegs(
				operation, points, sequence, pairsByLeg(prepared.pairs()), emissionsByLeg(response));
			operation.complete(response.totalEmissionKg(), sequence, legs);
			operation.registrarTempoExecucao(executionMillis);
		});
	}

	private Map<Leg, PairSegment> pairsByLeg(List<PairSegment> pairs) {
		Map<Leg, PairSegment> byLeg = new HashMap<>();
		pairs.forEach(pair -> byLeg.put(new Leg(pair.fromIndex(), pair.toIndex()), pair));
		return byLeg;
	}

	private Map<Leg, Double> emissionsByLeg(OptimizeResponse response) {
		Map<Leg, Double> byLeg = new HashMap<>();
		if (response.segmentEmissions() != null) {
			response.segmentEmissions().forEach(
				emission -> byLeg.put(new Leg(emission.fromIndex(), emission.toIndex()), emission.emissionKg()));
		}
		return byLeg;
	}

	private List<RouteSegment> buildLegs(
		Operation operation, List<DeliveryPoint> points, List<Integer> sequence,
		Map<Leg, PairSegment> pairs, Map<Leg, Double> emissions) {
		return IntStream.range(0, sequence.size() - 1)
			.mapToObj(position -> {
				Leg leg = new Leg(sequence.get(position), sequence.get(position + 1));
				Double emission = emissions.get(leg);
				if (emission == null) {
					throw new InvalidOptimizerResponseException(
						"Optimizer response has no emission for leg %d -> %d".formatted(leg.from(), leg.to()));
				}
				PairSegment pair = pairs.get(leg);
				DeliveryPoint origin = points.get(leg.from());
				DeliveryPoint destination = points.get(leg.to());
				return new RouteSegment(
					operation, origin, destination, position + 1,
					lineOf(pair, origin, destination),
					pair.averageSpeedKmh(), pair.slopePercent(), emission, pair.distanceMeters());
			})
			.toList();
	}

	private void validateSequence(List<Integer> sequence, int pointCount) {
		Set<Integer> expected = new HashSet<>(IntStream.range(0, pointCount).boxed().toList());
		if (sequence == null || sequence.size() != pointCount || !expected.equals(new HashSet<>(sequence))) {
			throw new InvalidOptimizerResponseException(
				"Optimizer returned an invalid visiting sequence for %d points: %s".formatted(pointCount, sequence));
		}
	}

	private void markFailed(Long operationId, Throwable error) {
		Throwable root = unwrap(error);
		String message = String.valueOf(root.getMessage());
		if (message.length() > MAX_ERROR_LENGTH) {
			message = message.substring(0, MAX_ERROR_LENGTH);
		}
		log.warn("Route operation {} failed: {}", operationId, message);
		String persistedMessage = message;
		try {
			transactions.executeWithoutResult(status -> loadOperation(operationId).fail(persistedMessage));
		}
		catch (RuntimeException persistenceError) {
			log.error("Could not record failure of route operation {}", operationId, persistenceError);
		}
	}

	private Throwable unwrap(Throwable error) {
		Throwable current = error;
		while ((current instanceof CompletionException || current instanceof ExecutionException)
			&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	private Operation loadOperation(Long operationId) {
		return operationRepository.findById(operationId)
			.orElseThrow(() -> new IllegalStateException("Operation %d not found".formatted(operationId)));
	}

	private List<DeliveryPoint> pointsOf(Operation operation) {
		return operation.getPoints().stream().map(OperationPoint::getPoint).toList();
	}

	private LineString lineOf(PairSegment pair, DeliveryPoint origin, DeliveryPoint destination) {
		if (pair.geometry() == null || pair.geometry().size() < 2) {
			return lineBetween(origin, destination);
		}
		List<Coordinate> path = new ArrayList<>();
		path.add(origin.getGeom().getCoordinate());
		pair.geometry().forEach(point -> path.add(new Coordinate(point.longitude(), point.latitude())));
		path.add(destination.getGeom().getCoordinate());
		return GEOMETRY_FACTORY.createLineString(path.toArray(new Coordinate[0]));
	}

	private LineString lineBetween(DeliveryPoint origin, DeliveryPoint destination) {
		return GEOMETRY_FACTORY.createLineString(new Coordinate[] {
			origin.getGeom().getCoordinate(), destination.getGeom().getCoordinate()});
	}

}
