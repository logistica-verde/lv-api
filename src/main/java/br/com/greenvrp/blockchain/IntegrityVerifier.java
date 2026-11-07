package br.com.greenvrp.blockchain;

import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.route.OperationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IntegrityVerifier {

	private record Snapshot(String databaseHash, Optional<BlockchainRecord> record) {
	}

	private final OperationRepository operationRepository;
	private final BlockchainRecordRepository recordRepository;
	private final HashGenerator hashGenerator;
	private final Ledger ledger;
	private final TransactionTemplate transactions;

	public IntegrityVerifier(
		OperationRepository operationRepository,
		BlockchainRecordRepository recordRepository,
		HashGenerator hashGenerator,
		Ledger ledger,
		PlatformTransactionManager transactionManager) {
		this.operationRepository = operationRepository;
		this.recordRepository = recordRepository;
		this.hashGenerator = hashGenerator;
		this.ledger = ledger;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public IntegrityReport verify(Long operationId) {
		Snapshot snapshot = transactions.execute(status -> {
			var operation = operationRepository.findById(operationId)
				.orElseThrow(() -> new ResourceNotFoundException("Route operation %d not found".formatted(operationId)));
			return new Snapshot(hashGenerator.hash(operation), recordRepository.findByOperationId(operationId));
		});

		Optional<String> ledgerHash = ledger.readHash(operationId);
		boolean gaveUp = snapshot.record().filter(record -> record.getStatus() == AnchorStatus.EXHAUSTED).isPresent();
		IntegrityStatus status = ledgerHash
			.map(onLedger -> onLedger.equalsIgnoreCase(snapshot.databaseHash()) ? IntegrityStatus.VALID : IntegrityStatus.TAMPERED)
			.orElse(gaveUp ? IntegrityStatus.ANCHOR_FAILED : IntegrityStatus.NOT_ANCHORED);
		Optional<BlockchainRecord> anchored = snapshot.record().filter(record -> record.getStatus() == AnchorStatus.ANCHORED);

		return new IntegrityReport(
			operationId,
			status,
			snapshot.databaseHash(),
			ledgerHash.orElse(null),
			anchored.map(BlockchainRecord::getTxHash).orElse(null),
			anchored.map(BlockchainRecord::getBlocoLedger).orElse(null),
			anchored.map(BlockchainRecord::getTimestampRegistro).orElse(null));
	}

}
