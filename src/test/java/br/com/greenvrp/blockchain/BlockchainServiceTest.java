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

	private static final int MAX_ATTEMPTS = 3;

	@Autowired
	private OperationRepository operationRepository;

	@Autowired
	private BlockchainRecordRepository recordRepository;

	@Autowired
	private AnchorAttemptRepository attemptRepository;

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
		service = new BlockchainService(
			operationRepository, recordRepository, attemptRepository, hashGenerator, ledger, transactionManager, MAX_ATTEMPTS);
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
		attemptRepository.deleteAll();
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
	void recordsTheContractAndTheSerializationVersionOfEveryRecord() {
		ledger.failWith(new LedgerException("Besu is down"));
		service.anchorOperation(operationId);
		BlockchainRecord failed = recordRepository.findByOperationId(operationId).orElseThrow();
		ledger.recover();
		service.retryFailed();

		BlockchainRecord anchored = recordRepository.findByOperationId(operationId).orElseThrow();
		assertThat(failed.getEnderecoContrato()).isEqualTo(InMemoryLedger.CONTRACT_ADDRESS);
		assertThat(anchored.getEnderecoContrato()).isEqualTo(InMemoryLedger.CONTRACT_ADDRESS);
		assertThat(anchored.getVersaoSerializacao()).isEqualTo(1);
	}

	@Test
	void givesUpAfterTheMaximumNumberOfAttemptsAndStopsRetrying() {
		ledger.failWith(new LedgerException("Besu is down"));
		service.anchorOperation(operationId);
		service.retryFailed();
		service.retryFailed();

		assertThat(recordRepository.findByOperationId(operationId).orElseThrow().getStatus())
			.isEqualTo(AnchorStatus.EXHAUSTED);
		ledger.recover();
		assertThat(service.retryFailed()).isZero();
		service.anchorOperation(operationId);
		assertThat(attemptRepository.countByOperationId(operationId)).isEqualTo(MAX_ATTEMPTS);
		assertThat(ledger.readHash(operationId)).isEmpty();
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
	void logsEveryAttemptAndRetriesAFailedAnchorOnTheSameRecord() {
		ledger.failWith(new LedgerException("Besu is down"));
		service.anchorOperation(operationId);
		ledger.recover();

		int retried = service.retryFailed();

		assertThat(retried).isEqualTo(1);
		assertThat(recordRepository.findAll()).hasSize(1);
		BlockchainRecord record = recordRepository.findByOperationId(operationId).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(AnchorStatus.ANCHORED);
		assertThat(record.getMensagemErro()).isNull();
		assertThat(attemptRepository.findByOperationIdOrderByNumberAsc(operationId))
			.extracting(AnchorAttempt::getNumber, AnchorAttempt::getResult)
			.containsExactly(
				org.assertj.core.groups.Tuple.tuple(1, AnchorAttemptResult.FAILED),
				org.assertj.core.groups.Tuple.tuple(2, AnchorAttemptResult.ANCHORED));
	}

	@Test
	void recoversTheRecordWithoutResendingWhenTheHashIsAlreadyOnTheLedger() {
		ledger.anchor(operationId, currentHash());

		service.anchorOperation(operationId);

		BlockchainRecord record = recordRepository.findByOperationId(operationId).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(AnchorStatus.ANCHORED);
		assertThat(record.getTxHash()).isNull();
		assertThat(attemptRepository.findByOperationIdOrderByNumberAsc(operationId))
			.extracting(AnchorAttempt::getResult)
			.containsExactly(AnchorAttemptResult.RECOVERED);
	}

	@Test
	void flagsADivergenceInsteadOfResendingWhenTheLedgerHoldsAnotherHash() {
		String foreignHash = "ab".repeat(32);
		ledger.anchor(operationId, foreignHash);

		service.anchorOperation(operationId);

		BlockchainRecord record = recordRepository.findByOperationId(operationId).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(AnchorStatus.DIVERGENT);
		assertThat(record.getHashSha256()).isEqualTo(currentHash());
		assertThat(record.getMensagemErro()).contains(foreignHash).contains("never sent");
		assertThat(ledger.readHash(operationId)).contains(foreignHash);
		assertThat(attemptRepository.findByOperationIdOrderByNumberAsc(operationId))
			.extracting(AnchorAttempt::getResult)
			.containsExactly(AnchorAttemptResult.DIVERGENT);
	}

	@Test
	void tellsWhenTheLedgerHoldsTheHashSentEarlierButTheOperationChangedSince() {
		ledger.failWith(new LedgerException("Timed out waiting for the receipt"));
		service.anchorOperation(operationId);
		String sentHash = currentHash();
		ledger.recover();
		ledger.anchor(operationId, sentHash);
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
			operationRepository.findById(operationId).orElseThrow().complete(9.9, List.of(0, 1), List.of()));

		service.anchorOperation(operationId);

		BlockchainRecord record = recordRepository.findByOperationId(operationId).orElseThrow();
		assertThat(record.getStatus()).isEqualTo(AnchorStatus.DIVERGENT);
		assertThat(record.getMensagemErro()).contains(sentHash).contains("earlier attempt");
		assertThat(attemptRepository.findByOperationIdOrderByNumberAsc(operationId))
			.extracting(AnchorAttempt::getResult)
			.containsExactly(AnchorAttemptResult.FAILED, AnchorAttemptResult.DIVERGENT);
	}

	@Test
	void neverRetriesOrReanchorsADivergentOperation() {
		ledger.anchor(operationId, "ab".repeat(32));
		service.anchorOperation(operationId);

		assertThat(service.retryFailed()).isZero();
		service.anchorOperation(operationId);

		assertThat(attemptRepository.countByOperationId(operationId)).isEqualTo(1);
		assertThat(recordRepository.findByOperationId(operationId).orElseThrow().getStatus())
			.isEqualTo(AnchorStatus.DIVERGENT);
	}

	@Test
	void doesNotRetryAnAnchorThatSucceeded() {
		service.anchorOperation(operationId);

		assertThat(service.retryFailed()).isZero();
		assertThat(attemptRepository.countByOperationId(operationId)).isEqualTo(1);
	}

	@Test
	void keepsTheRecordFailedWhenTheRetryFailsToo() {
		ledger.failWith(new LedgerException("Besu is down"));
		service.anchorOperation(operationId);

		service.retryFailed();

		assertThat(recordRepository.findByOperationId(operationId).orElseThrow().getStatus())
			.isEqualTo(AnchorStatus.FAILED);
		assertThat(attemptRepository.countByOperationId(operationId)).isEqualTo(2);
	}

	@Test
	void neverThrowsEvenForAnUnknownOperation() {
		service.anchorOperation(999_999L);

		assertThat(recordRepository.findAll()).isEmpty();
	}

}
