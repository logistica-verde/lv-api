package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BlockchainServiceTest {

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

	private final HashGenerator hashGenerator = new HashGenerator();
	private final InMemoryLedger ledger = new InMemoryLedger();
	private BlockchainService service;
	private Long operationId;

	@BeforeEach
	void setUp() {
		service = new BlockchainService(operationRepository, recordRepository, hashGenerator, ledger, transactionManager);
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

	private String currentHash() {
		return new TransactionTemplate(transactionManager).execute(status ->
			hashGenerator.hash(operationRepository.findById(operationId).orElseThrow()));
	}

	@Test
	void anchorsTheOperationHashAndStoresTheReceipt() {
		service.anchorOperation(operationId);

		BlockchainRecord record = recordRepository.findByOperationId(operationId).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(AnchorStatus.ANCHORED);
		assertThat(record.getHashSha256()).isEqualTo(currentHash()).hasSize(64);
		assertThat(record.getTxHash()).isEqualTo("0x%064x".formatted(operationId));
		assertThat(record.getBlocoLedger()).isEqualTo(101L);
		assertThat(record.getMensagemErro()).isNull();
		assertThat(ledger.readHash(operationId)).contains(currentHash());
	}

	@Test
	void recordsFailureWhenTheLedgerIsUnavailable() {
		ledger.failWith(new LedgerException("Besu is down"));

		service.anchorOperation(operationId);

		BlockchainRecord record = recordRepository.findByOperationId(operationId).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(AnchorStatus.FAILED);
		assertThat(record.getMensagemErro()).contains("Besu is down");
		assertThat(record.getTxHash()).isNull();
		assertThat(record.getHashSha256()).isEqualTo(currentHash());
	}

	@Test
	void doesNotAnchorTheSameOperationTwice() {
		service.anchorOperation(operationId);
		service.anchorOperation(operationId);

		assertThat(recordRepository.findAll()).hasSize(1);
		assertThat(recordRepository.findByOperationId(operationId).orElseThrow().getStatus())
			.isEqualTo(AnchorStatus.ANCHORED);
	}

	@Test
	void neverThrowsEvenForAnUnknownOperation() {
		service.anchorOperation(999_999L);

		assertThat(recordRepository.findAll()).isEmpty();
	}

}
