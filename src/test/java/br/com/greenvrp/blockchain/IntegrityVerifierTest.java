package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.OperationRepository;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IntegrityVerifierTest {

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

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final HashGenerator hashGenerator = new HashGenerator();
	private final InMemoryLedger ledger = new InMemoryLedger();
	private BlockchainService blockchainService;
	private IntegrityVerifier verifier;
	private Long operationId;

	@BeforeEach
	void setUp() {
		blockchainService = new BlockchainService(
			operationRepository, recordRepository, hashGenerator, ledger, transactionManager);
		verifier = new IntegrityVerifier(
			operationRepository, recordRepository, hashGenerator, ledger, transactionManager);
		Vehicle vehicle = vehicleRepository.save(new Vehicle("ABC1D23", 7500.0, "diesel"));
		List<DeliveryPoint> points = List.of(
			deliveryPointRepository.save(new DeliveryPoint("A", null, -23.55, -46.63)),
			deliveryPointRepository.save(new DeliveryPoint("B", null, -23.56, -46.64)));
		operationId = operationRepository
			.save(Operation.pending(vehicle, OptimizationCriterion.EMISSION, points))
			.getId();
	}

	@AfterEach
	void cleanUp() {
		recordRepository.deleteAll();
		operationRepository.deleteAll();
		deliveryPointRepository.deleteAll();
		vehicleRepository.deleteAll();
	}

	@Test
	void reportsValidWhenTheDatabaseMatchesTheLedger() {
		blockchainService.anchorOperation(operationId);

		IntegrityReport report = verifier.verify(operationId);

		assertThat(report.status()).isEqualTo(IntegrityStatus.VALID);
		assertThat(report.databaseHash()).isEqualTo(report.ledgerHash());
		assertThat(report.txHash()).isEqualTo("0x%064x".formatted(operationId));
		assertThat(report.blockNumber()).isEqualTo(101L);
		assertThat(report.anchoredAt()).isNotNull();
	}

	@Test
	void reportsTamperedWhenStoredDataChangesAfterAnchoring() {
		blockchainService.anchorOperation(operationId);
		jdbcTemplate.update("update operacoes set criterio = 'DISTANCE' where id = ?", operationId);

		IntegrityReport report = verifier.verify(operationId);

		assertThat(report.status()).isEqualTo(IntegrityStatus.TAMPERED);
		assertThat(report.databaseHash()).isNotEqualTo(report.ledgerHash());
	}

	@Test
	void stillDetectsTamperingWhenTheLocalRecordWasAlsoDeleted() {
		blockchainService.anchorOperation(operationId);
		jdbcTemplate.update("update operacoes set criterio = 'DISTANCE' where id = ?", operationId);
		recordRepository.deleteAll();

		IntegrityReport report = verifier.verify(operationId);

		assertThat(report.status()).isEqualTo(IntegrityStatus.TAMPERED);
		assertThat(report.txHash()).isNull();
	}

	@Test
	void reportsNotAnchoredWhenTheLedgerHasNoHash() {
		IntegrityReport report = verifier.verify(operationId);

		assertThat(report.status()).isEqualTo(IntegrityStatus.NOT_ANCHORED);
		assertThat(report.ledgerHash()).isNull();
		assertThat(report.databaseHash()).hasSize(64);
	}

	@Test
	void rejectsUnknownOperations() {
		assertThatThrownBy(() -> verifier.verify(999_999L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void propagatesLedgerFailures() {
		ledger.failWith(new LedgerException("Besu is down"));

		assertThatThrownBy(() -> verifier.verify(operationId))
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("Besu is down");
	}

}
