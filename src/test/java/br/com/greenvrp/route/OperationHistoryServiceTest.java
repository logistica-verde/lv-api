package br.com.greenvrp.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.blockchain.AnchorReceipt;
import br.com.greenvrp.blockchain.BlockchainRecord;
import br.com.greenvrp.blockchain.BlockchainRecordRepository;
import br.com.greenvrp.blockchain.IntegrityReport;
import br.com.greenvrp.blockchain.IntegrityStatus;
import br.com.greenvrp.blockchain.IntegrityVerifier;
import br.com.greenvrp.blockchain.LedgerException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@DataJpaTest
class OperationHistoryServiceTest {

	private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private OperationRepository operationRepository;

	@Autowired
	private BlockchainRecordRepository recordRepository;

	@Autowired
	private VehicleRepository vehicleRepository;

	@Autowired
	private DeliveryPointRepository deliveryPointRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final IntegrityVerifier verifier = mock(IntegrityVerifier.class);

	private OperationHistoryService service;
	private Vehicle first;
	private Vehicle second;
	private List<DeliveryPoint> points;

	@BeforeEach
	void setUp() {
		service = serviceWith(1000);
		first = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		second = vehicleRepository.save(new Vehicle("XYZ9K88", 12000.0, "diesel"));
		points = List.of(
			deliveryPointRepository.save(new DeliveryPoint("P1", null, -23.55, -46.63)),
			deliveryPointRepository.save(new DeliveryPoint("P2", null, -23.56, -46.64)));
	}

	private OperationHistoryService serviceWith(int integrityLimit) {
		return new OperationHistoryService(
			operationRepository, recordRepository, verifier,
			new HistoryProperties(ZONE, integrityLimit), transactionManager);
	}

	private Operation operation(
		Vehicle vehicle, OptimizationCriterion criterion, String createdAt, Double emission, Double distance) {
		Operation operation = Operation.pending(vehicle, criterion, points);
		ReflectionTestUtils.setField(operation, "dataHora", Instant.parse(createdAt));
		if (emission != null) {
			ReflectionTestUtils.setField(operation, "status", OperationStatus.COMPLETED);
			ReflectionTestUtils.setField(operation, "emissaoTotalKg", emission);
			ReflectionTestUtils.setField(operation, "distanciaTotalMetros", distance);
		}
		return operationRepository.saveAndFlush(operation);
	}

	private HistoryQuery query() {
		return new HistoryQuery(null, null, null, null, null, HistorySort.CREATED_AT, false, 1, 10);
	}

	private HistoryQuery query(HistorySort sort, boolean ascending, int page, int size) {
		return new HistoryQuery(null, null, null, null, null, sort, ascending, page, size);
	}

	private void anchor(Operation operation, IntegrityStatus verdict) {
		recordRepository.saveAndFlush(
			BlockchainRecord.anchored(operation, "a".repeat(64), new AnchorReceipt("0xabc", 120)));
		given(verifier.verify(operation.getId())).willReturn(
			new IntegrityReport(operation.getId(), verdict, "a".repeat(64), "a".repeat(64), "0xabc", 120L, Instant.now()));
	}

	private List<Long> ids(OperationPageResponse page) {
		return page.items().stream().map(OperationSummaryResponse::id).toList();
	}

	@Test
	void listsNewestFirstByDefaultWithTheFieldsTheScreenShows() {
		Operation older = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.5, 2010.0);
		Operation newer = operation(second, OptimizationCriterion.DISTANCE, "2026-10-02T12:00:00Z", 6.5, 1900.0);

		OperationPageResponse page = service.list(query());

		assertThat(ids(page)).containsExactly(newer.getId(), older.getId());
		assertThat(page.total()).isEqualTo(2);
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.size()).isEqualTo(10);
		OperationSummaryResponse item = page.items().get(0);
		assertThat(item.vehicleId()).isEqualTo(second.getId());
		assertThat(item.vehiclePlate()).isEqualTo("XYZ9K88");
		assertThat(item.criterion()).isEqualTo(OptimizationCriterion.DISTANCE);
		assertThat(item.status()).isEqualTo(OperationStatus.COMPLETED);
		assertThat(item.totalEmissionKg()).isEqualTo(6.5);
		assertThat(item.totalDistanceMeters()).isEqualTo(1900.0);
		assertThat(item.createdAt()).isEqualTo(Instant.parse("2026-10-02T12:00:00Z"));
	}

	@Test
	void showsPendingOperationsWithoutTotals() {
		operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", null, null);

		OperationSummaryResponse item = service.list(query()).items().get(0);

		assertThat(item.status()).isEqualTo(OperationStatus.PENDING);
		assertThat(item.totalEmissionKg()).isNull();
		assertThat(item.totalDistanceMeters()).isNull();
	}

	@Test
	void filtersByVehicleAndByCriterion() {
		Operation a = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.0, 1000.0);
		Operation b = operation(first, OptimizationCriterion.DISTANCE, "2026-10-01T13:00:00Z", 6.0, 900.0);
		Operation c = operation(second, OptimizationCriterion.EMISSION, "2026-10-01T14:00:00Z", 7.0, 1100.0);

		HistoryQuery byVehicle = new HistoryQuery(
			null, null, first.getId(), null, null, HistorySort.CREATED_AT, false, 1, 10);
		HistoryQuery byCriterion = new HistoryQuery(
			null, null, null, OptimizationCriterion.EMISSION, null, HistorySort.CREATED_AT, false, 1, 10);
		HistoryQuery both = new HistoryQuery(
			null, null, second.getId(), OptimizationCriterion.EMISSION, null, HistorySort.CREATED_AT, false, 1, 10);

		assertThat(ids(service.list(byVehicle))).containsExactly(b.getId(), a.getId());
		assertThat(ids(service.list(byCriterion))).containsExactly(c.getId(), a.getId());
		assertThat(ids(service.list(both))).containsExactly(c.getId());
	}

	@Test
	void filtersByDateInclusivelyUsingTheLocalDay() {
		Operation lateNight = operation(first, OptimizationCriterion.EMISSION, "2026-10-05T02:30:00Z", 5.0, 1000.0);
		Operation nextMorning = operation(first, OptimizationCriterion.EMISSION, "2026-10-05T12:00:00Z", 5.0, 1000.0);
		Operation twoDaysLater = operation(first, OptimizationCriterion.EMISSION, "2026-10-07T12:00:00Z", 5.0, 1000.0);

		HistoryQuery fourth = new HistoryQuery(
			LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 4), null, null, null, HistorySort.CREATED_AT, false, 1, 10);
		HistoryQuery fourthToSixth = new HistoryQuery(
			LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 6), null, null, null, HistorySort.CREATED_AT, false, 1, 10);
		HistoryQuery fromFifth = new HistoryQuery(
			LocalDate.of(2026, 10, 5), null, null, null, null, HistorySort.CREATED_AT, false, 1, 10);
		HistoryQuery upToFifth = new HistoryQuery(
			null, LocalDate.of(2026, 10, 5), null, null, null, HistorySort.CREATED_AT, false, 1, 10);

		assertThat(ids(service.list(fourth))).containsExactly(lateNight.getId());
		assertThat(ids(service.list(fourthToSixth))).containsExactly(nextMorning.getId(), lateNight.getId());
		assertThat(ids(service.list(fromFifth))).containsExactly(twoDaysLater.getId(), nextMorning.getId());
		assertThat(ids(service.list(upToFifth))).containsExactly(nextMorning.getId(), lateNight.getId());
	}

	@Test
	void sortsByEmissionAndByDistanceInBothDirections() {
		Operation low = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 2.0, 3000.0);
		Operation mid = operation(first, OptimizationCriterion.EMISSION, "2026-10-02T12:00:00Z", 4.0, 1000.0);
		Operation high = operation(first, OptimizationCriterion.EMISSION, "2026-10-03T12:00:00Z", 6.0, 2000.0);

		assertThat(ids(service.list(query(HistorySort.TOTAL_EMISSION_KG, true, 1, 10))))
			.containsExactly(low.getId(), mid.getId(), high.getId());
		assertThat(ids(service.list(query(HistorySort.TOTAL_EMISSION_KG, false, 1, 10))))
			.containsExactly(high.getId(), mid.getId(), low.getId());
		assertThat(ids(service.list(query(HistorySort.TOTAL_DISTANCE_METERS, true, 1, 10))))
			.containsExactly(mid.getId(), high.getId(), low.getId());
		assertThat(ids(service.list(query(HistorySort.TOTAL_DISTANCE_METERS, false, 1, 10))))
			.containsExactly(low.getId(), high.getId(), mid.getId());
		assertThat(ids(service.list(query(HistorySort.CREATED_AT, true, 1, 10))))
			.containsExactly(low.getId(), mid.getId(), high.getId());
	}

	@Test
	void breaksTiesByNewestIdSoPagesStayStable() {
		Operation a = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.0, 1000.0);
		Operation b = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.0, 1000.0);

		assertThat(ids(service.list(query(HistorySort.TOTAL_EMISSION_KG, true, 1, 10))))
			.containsExactly(b.getId(), a.getId());
	}

	@Test
	void pagesTheResultsAndReportsTheTotal() {
		for (int day = 1; day <= 5; day++) {
			operation(first, OptimizationCriterion.EMISSION, "2026-10-0" + day + "T12:00:00Z", day * 1.0, 1000.0);
		}

		OperationPageResponse firstPage = service.list(query(HistorySort.CREATED_AT, true, 1, 2));
		OperationPageResponse lastPage = service.list(query(HistorySort.CREATED_AT, true, 3, 2));
		OperationPageResponse beyond = service.list(query(HistorySort.CREATED_AT, true, 4, 2));

		assertThat(firstPage.items()).extracting(OperationSummaryResponse::totalEmissionKg).containsExactly(1.0, 2.0);
		assertThat(lastPage.items()).extracting(OperationSummaryResponse::totalEmissionKg).containsExactly(5.0);
		assertThat(beyond.items()).isEmpty();
		assertThat(firstPage.total()).isEqualTo(5);
		assertThat(beyond.total()).isEqualTo(5);
		assertThat(lastPage.page()).isEqualTo(3);
		assertThat(lastPage.size()).isEqualTo(2);
	}

	@Test
	void returnsAnEmptyPageWhenNothingMatches() {
		OperationPageResponse page = service.list(query());

		assertThat(page.items()).isEmpty();
		assertThat(page.total()).isZero();
	}

	@Test
	void reportsTheIntegrityOfEachOperationAndSkipsTheLedgerForUnanchoredOnes() {
		Operation valid = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.0, 1000.0);
		Operation tampered = operation(first, OptimizationCriterion.DISTANCE, "2026-10-02T12:00:00Z", 6.0, 900.0);
		Operation unanchored = operation(first, OptimizationCriterion.EMISSION, "2026-10-03T12:00:00Z", 7.0, 1100.0);
		anchor(valid, IntegrityStatus.VALID);
		anchor(tampered, IntegrityStatus.TAMPERED);

		OperationPageResponse page = service.list(query());

		assertThat(page.items())
			.extracting(OperationSummaryResponse::id, OperationSummaryResponse::integrityStatus)
			.containsExactly(
				tuple(unanchored.getId(), IntegrityStatus.NOT_ANCHORED),
				tuple(tampered.getId(), IntegrityStatus.TAMPERED),
				tuple(valid.getId(), IntegrityStatus.VALID));
		verify(verifier, never()).verify(unanchored.getId());
	}

	@Test
	void treatsAFailedAnchorAsNotAnchored() {
		Operation failed = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.0, 1000.0);
		recordRepository.saveAndFlush(BlockchainRecord.failed(failed, "a".repeat(64), "ledger down"));

		OperationSummaryResponse item = service.list(query()).items().get(0);

		assertThat(item.integrityStatus()).isEqualTo(IntegrityStatus.NOT_ANCHORED);
		verify(verifier, never()).verify(anyLong());
	}

	@Test
	void filtersByIntegrityAndPagesTheFilteredResult() {
		Operation valid = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.0, 1000.0);
		Operation tamperedOne = operation(first, OptimizationCriterion.EMISSION, "2026-10-02T12:00:00Z", 6.0, 900.0);
		Operation tamperedTwo = operation(first, OptimizationCriterion.EMISSION, "2026-10-03T12:00:00Z", 7.0, 1100.0);
		operation(first, OptimizationCriterion.EMISSION, "2026-10-04T12:00:00Z", 8.0, 1200.0);
		anchor(valid, IntegrityStatus.VALID);
		anchor(tamperedOne, IntegrityStatus.TAMPERED);
		anchor(tamperedTwo, IntegrityStatus.TAMPERED);

		HistoryQuery tampered = new HistoryQuery(
			null, null, null, null, IntegrityStatus.TAMPERED, HistorySort.CREATED_AT, false, 1, 1);
		HistoryQuery tamperedSecondPage = new HistoryQuery(
			null, null, null, null, IntegrityStatus.TAMPERED, HistorySort.CREATED_AT, false, 2, 1);
		HistoryQuery notAnchored = new HistoryQuery(
			null, null, null, null, IntegrityStatus.NOT_ANCHORED, HistorySort.CREATED_AT, false, 1, 10);

		OperationPageResponse firstPage = service.list(tampered);
		OperationPageResponse secondPage = service.list(tamperedSecondPage);

		assertThat(ids(firstPage)).containsExactly(tamperedTwo.getId());
		assertThat(ids(secondPage)).containsExactly(tamperedOne.getId());
		assertThat(firstPage.total()).isEqualTo(2);
		assertThat(service.list(notAnchored).total()).isEqualTo(1);
		assertThat(service.list(new HistoryQuery(
			null, null, null, null, IntegrityStatus.VALID, HistorySort.CREATED_AT, false, 1, 10)).total()).isEqualTo(1);
	}

	@Test
	void combinesTheIntegrityFilterWithTheOthers() {
		Operation mine = operation(first, OptimizationCriterion.EMISSION, "2026-10-02T12:00:00Z", 6.0, 900.0);
		Operation other = operation(second, OptimizationCriterion.EMISSION, "2026-10-03T12:00:00Z", 7.0, 1100.0);
		anchor(mine, IntegrityStatus.TAMPERED);
		anchor(other, IntegrityStatus.TAMPERED);

		HistoryQuery query = new HistoryQuery(
			null, null, first.getId(), null, IntegrityStatus.TAMPERED, HistorySort.CREATED_AT, false, 1, 10);

		assertThat(ids(service.list(query))).containsExactly(mine.getId());
	}

	@Test
	void refusesToFilterByIntegrityOverTooManyOperations() {
		for (int day = 1; day <= 3; day++) {
			operation(first, OptimizationCriterion.EMISSION, "2026-10-0" + day + "T12:00:00Z", day * 1.0, 1000.0);
		}
		HistoryQuery query = new HistoryQuery(
			null, null, null, null, IntegrityStatus.VALID, HistorySort.CREATED_AT, false, 1, 10);

		assertThatThrownBy(() -> serviceWith(2).list(query))
			.isInstanceOf(InvalidRequestException.class)
			.hasMessageContaining("Too many operations");
		assertThat(serviceWith(3).list(query).total()).isZero();
	}

	@Test
	void failsTheWholePageWhenTheLedgerIsUnavailable() {
		Operation anchored = operation(first, OptimizationCriterion.EMISSION, "2026-10-01T12:00:00Z", 5.0, 1000.0);
		recordRepository.saveAndFlush(
			BlockchainRecord.anchored(anchored, "a".repeat(64), new AnchorReceipt("0xabc", 120)));
		given(verifier.verify(anchored.getId())).willThrow(new LedgerException("ledger unreachable"));

		assertThatThrownBy(() -> service.list(query())).isInstanceOf(LedgerException.class);
	}

}
