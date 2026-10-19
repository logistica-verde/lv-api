package br.com.greenvrp.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.greenvrp.blockchain.BlockchainService;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.geo.PairSegmentSource;
import br.com.greenvrp.geo.GeoPoint;
import br.com.greenvrp.geo.PairSegment;
import br.com.greenvrp.geo.RoadDataNotFoundException;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.optimizer.OptimizeRequest;
import br.com.greenvrp.optimizer.OptimizeResponse;
import br.com.greenvrp.optimizer.OptimizeResponse.SegmentEmission;
import br.com.greenvrp.optimizer.OptimizerClient;
import br.com.greenvrp.optimizer.OptimizerException;
import br.com.greenvrp.settings.GaSettingsService;
import br.com.greenvrp.settings.GaSettingsValues;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RouteProcessorTest {

	@Autowired
	private OperationRepository operationRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final PairSegmentSource geoQueryService = mock(PairSegmentSource.class);
	private final OptimizerClient optimizerClient = mock(OptimizerClient.class);
	private final BlockchainService blockchainService = mock(BlockchainService.class);
	private final GaSettingsService gaSettingsService = mock(GaSettingsService.class);

	private RouteProcessor processor;
	private Long operationId;
	private List<Long> pointIds;

	@BeforeEach
	void setUp() {
		when(gaSettingsService.current()).thenReturn(GaSettingsValues.DEFAULT);
		processor = new RouteProcessor(
			operationRepository, geoQueryService, optimizerClient, blockchainService, gaSettingsService,
			transactionManager, Runnable::run);

		Vehicle vehicle = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		List<DeliveryPoint> points = List.of(
			deliveryPointRepository.save(new DeliveryPoint("P1", null, -23.55, -46.63)),
			deliveryPointRepository.save(new DeliveryPoint("P2", null, -23.56, -46.64)),
			deliveryPointRepository.save(new DeliveryPoint("P3", null, -23.57, -46.65)));
		pointIds = points.stream().map(DeliveryPoint::getId).toList();
		operationId = operationRepository
			.save(Operation.pending(vehicle, OptimizationCriterion.EMISSION, points))
			.getId();

		when(geoQueryService.buildPairSegments(anyList())).thenReturn(allPairs(3));
	}

	@AfterEach
	void cleanUp() {
		operationRepository.deleteAll();
		deliveryPointRepository.deleteAll();
		vehicleRepository.deleteAll();
	}

	private List<PairSegment> allPairs(int count) {
		List<PairSegment> pairs = new ArrayList<>();
		for (int from = 0; from < count; from++) {
			for (int to = 0; to < count; to++) {
				if (from != to) {
					pairs.add(new PairSegment(from, to, 1000.0 + from * 10 + to, 40.0, 2.0));
				}
			}
		}
		return pairs;
	}

	private <T> T inTransaction(java.util.function.Supplier<T> action) {
		return new TransactionTemplate(transactionManager).execute(status -> action.get());
	}

	@Test
	void sendsTheVehicleMassPlusTheCargoToTheOptimizer() {
		Vehicle vehicle = vehicleRepository.findAll().get(0);
		List<DeliveryPoint> points = deliveryPointRepository.findAllById(pointIds);
		Long loadedId = operationRepository
			.save(Operation.pending(vehicle, OptimizationCriterion.EMISSION, points, 1500.0))
			.getId();
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.completedFuture(
			new OptimizeResponse(
				List.of(0, 1, 2), 5.5, 2010.0,
				List.of(new SegmentEmission(0, 1, 2.0), new SegmentEmission(1, 2, 3.5)))));

		processor.processAsync(loadedId).join();

		ArgumentCaptor<OptimizeRequest> sent = ArgumentCaptor.forClass(OptimizeRequest.class);
		verify(optimizerClient).optimize(sent.capture());
		assertThat(sent.getValue().vehicleMassKg()).isEqualTo(9000.0);
	}

	@Test
	void completesOperationAndPersistsOptimizedVisitOrderAndSegments() {
		OptimizeResponse response = new OptimizeResponse(
			List.of(1, 0, 2), 5.5, 2010.0,
			List.of(new SegmentEmission(1, 0, 2.0), new SegmentEmission(0, 2, 3.5)));
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.completedFuture(response));

		processor.processAsync(operationId).join();

		verify(blockchainService).anchorOperation(operationId);
		inTransaction(() -> {
			Operation operation = operationRepository.findById(operationId).orElseThrow();
			assertThat(operation.getStatus()).isEqualTo(OperationStatus.COMPLETED);
			assertThat(operation.getEmissaoTotalKg()).isEqualTo(5.5);
			assertThat(operation.getMensagemErro()).isNull();
			assertThat(operation.getPoints())
				.extracting(item -> item.getPoint().getNome(), OperationPoint::getOrdemVisita)
				.containsExactlyInAnyOrder(tuple("P2", 1), tuple("P1", 2), tuple("P3", 3));
			assertThat(operation.getSegments())
				.extracting(
					segment -> segment.getOrigin().getId(),
					segment -> segment.getDestination().getId(),
					RouteSegment::getEmissaoKgCo2,
					RouteSegment::getDistanciaMetros)
				.containsExactly(
					tuple(pointIds.get(1), pointIds.get(0), 2.0, 1010.0),
					tuple(pointIds.get(0), pointIds.get(2), 3.5, 1002.0));
			return null;
		});
	}

	@Test
	void keepsTheStreetGeometryOfEachLegBetweenTheDeliveryPoints() {
		List<GeoPoint> street = List.of(new GeoPoint(-23.5502, -46.6302), new GeoPoint(-23.5601, -46.6350));
		List<PairSegment> pairs = new ArrayList<>(allPairs(3));
		pairs.replaceAll(pair -> pair.fromIndex() == 1 && pair.toIndex() == 0
			? new PairSegment(1, 0, pair.distanceMeters(), pair.averageSpeedKmh(), pair.slopePercent(), street)
			: pair);
		when(geoQueryService.buildPairSegments(anyList())).thenReturn(pairs);
		OptimizeResponse response = new OptimizeResponse(
			List.of(1, 0, 2), 5.5, 2010.0,
			List.of(new SegmentEmission(1, 0, 2.0), new SegmentEmission(0, 2, 3.5)));
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.completedFuture(response));

		processor.processAsync(operationId).join();

		inTransaction(() -> {
			Operation operation = operationRepository.findById(operationId).orElseThrow();
			Coordinate[] followed = operation.getSegments().get(0).getGeom().getCoordinates();
			assertThat(followed).hasSize(4);
			assertThat(followed[0].getY()).isEqualTo(-23.56);
			assertThat(followed[1].getY()).isEqualTo(-23.5502);
			assertThat(followed[1].getX()).isEqualTo(-46.6302);
			assertThat(followed[3].getY()).isEqualTo(-23.55);
			Coordinate[] straight = operation.getSegments().get(1).getGeom().getCoordinates();
			assertThat(straight).hasSize(2);
			return null;
		});
	}

	@Test
	void recordsHowLongTheOptimizerTook() {
		OptimizeResponse response = new OptimizeResponse(
			List.of(1, 0, 2), 5.5, 2010.0,
			List.of(new SegmentEmission(1, 0, 2.0), new SegmentEmission(0, 2, 3.5)));
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.supplyAsync(() -> {
			try {
				Thread.sleep(60);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			return response;
		}));

		processor.processAsync(operationId).join();

		inTransaction(() -> {
			Operation operation = operationRepository.findById(operationId).orElseThrow();
			assertThat(operation.getTempoExecucaoMs()).isGreaterThanOrEqualTo(50).isLessThan(5000);
			return null;
		});
	}

	@Test
	void leavesTheExecutionTimeEmptyWhenTheOperationFails() {
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.failedFuture(
			new OptimizerException("Optimizer call failed: boom", new RuntimeException())));

		processor.processAsync(operationId).join();

		inTransaction(() -> {
			Operation operation = operationRepository.findById(operationId).orElseThrow();
			assertThat(operation.getStatus()).isEqualTo(OperationStatus.FAILED);
			assertThat(operation.getTempoExecucaoMs()).isNull();
			return null;
		});
	}

	@Test
	void sendsTheConfiguredGeneticAlgorithmParametersToTheOptimizer() {
		when(gaSettingsService.current()).thenReturn(new GaSettingsValues(250, 400, 0.9, 0.1));
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.failedFuture(
			new OptimizerException("Optimizer call failed: stop here", new RuntimeException())));

		processor.processAsync(operationId).join();

		ArgumentCaptor<OptimizeRequest> sent = ArgumentCaptor.forClass(OptimizeRequest.class);
		verify(optimizerClient).optimize(sent.capture());
		assertThat(sent.getValue().gaParams())
			.isEqualTo(new OptimizeRequest.GaParams(250, 400, 0.9, 0.1));
	}

	@Test
	void sendsAFixedSeedSoTheSameStopsGiveTheSameRoute() {
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.failedFuture(
			new OptimizerException("Optimizer call failed: stop here", new RuntimeException())));

		processor.processAsync(operationId).join();
		processor.processAsync(operationId).join();

		ArgumentCaptor<OptimizeRequest> sent = ArgumentCaptor.forClass(OptimizeRequest.class);
		verify(optimizerClient, times(2)).optimize(sent.capture());
		assertThat(sent.getAllValues().get(0).seed()).isNotNull();
		assertThat(sent.getAllValues().get(0).seed()).isEqualTo(sent.getAllValues().get(1).seed());
	}

	@Test
	void theSeedIsNeverNegativeEvenWithTheMaximumNumberOfPoints() {
		List<Long> manyPoints = new ArrayList<>();
		for (long id = 1_000_000L; id < 1_000_031L; id++) {
			manyPoints.add(id);
		}

		assertThat(RouteProcessor.seedOf(manyPoints)).isGreaterThanOrEqualTo(0);
		assertThat(RouteProcessor.seedOf(List.of(Long.MAX_VALUE, Long.MAX_VALUE))).isGreaterThanOrEqualTo(0);
	}

	@Test
	void theSeedDependsOnTheStopsAndTheirOrder() {
		assertThat(RouteProcessor.seedOf(List.of(1L, 2L, 3L))).isEqualTo(RouteProcessor.seedOf(List.of(1L, 2L, 3L)));
		assertThat(RouteProcessor.seedOf(List.of(1L, 2L, 3L))).isNotEqualTo(RouteProcessor.seedOf(List.of(1L, 3L, 2L)));
	}

	@Test
	void sendsTheDefaultParametersWhenNothingWasConfigured() {
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.failedFuture(
			new OptimizerException("Optimizer call failed: stop here", new RuntimeException())));

		processor.processAsync(operationId).join();

		ArgumentCaptor<OptimizeRequest> sent = ArgumentCaptor.forClass(OptimizeRequest.class);
		verify(optimizerClient).optimize(sent.capture());
		assertThat(sent.getValue().gaParams()).isEqualTo(new OptimizeRequest.GaParams(300, 500, 0.9, 0.4));
	}

	@Test
	void marksOperationFailedWhenOptimizerCallFails() {
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.failedFuture(
			new OptimizerException("Optimizer call failed: connection refused", new RuntimeException())));

		processor.processAsync(operationId).join();

		Operation operation = operationRepository.findById(operationId).orElseThrow();
		assertThat(operation.getStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(operation.getMensagemErro()).contains("connection refused");
		verifyNoInteractions(blockchainService);
	}

	@Test
	void marksOperationFailedWhenOptimizerReturnsInvalidSequence() {
		OptimizeResponse response = new OptimizeResponse(
			List.of(0, 0, 1), 5.5, 2010.0, List.of(new SegmentEmission(0, 1, 2.0)));
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.completedFuture(response));

		processor.processAsync(operationId).join();

		Operation operation = operationRepository.findById(operationId).orElseThrow();
		assertThat(operation.getStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(operation.getMensagemErro()).contains("invalid visiting sequence");
		verifyNoInteractions(blockchainService);
	}

	@Test
	void marksOperationFailedWhenOptimizerOmitsALegEmission() {
		OptimizeResponse response = new OptimizeResponse(
			List.of(0, 1, 2), 5.5, 2010.0, List.of(new SegmentEmission(0, 1, 2.0)));
		when(optimizerClient.optimize(any())).thenReturn(CompletableFuture.completedFuture(response));

		processor.processAsync(operationId).join();

		Operation operation = operationRepository.findById(operationId).orElseThrow();
		assertThat(operation.getStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(operation.getMensagemErro()).contains("no emission for leg 1 -> 2");
		verifyNoInteractions(blockchainService);
	}

	@Test
	void marksOperationFailedWhenAPointHasNoRoadData() {
		when(geoQueryService.buildPairSegments(anyList()))
			.thenThrow(new RoadDataNotFoundException("No road segment found for delivery point 'P1'"));

		processor.processAsync(operationId).join();

		Operation operation = operationRepository.findById(operationId).orElseThrow();
		assertThat(operation.getStatus()).isEqualTo(OperationStatus.FAILED);
		assertThat(operation.getMensagemErro()).contains("No road segment found");
		verifyNoInteractions(blockchainService);
	}

}
