package br.com.greenvrp.blockchain;

import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.OperationRepository;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BlockchainService {

	private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);
	private static final int MAX_ERROR_LENGTH = 500;

	private final OperationRepository operationRepository;
	private final BlockchainRecordRepository recordRepository;
	private final HashGenerator hashGenerator;
	private final Ledger ledger;
	private final TransactionTemplate transactions;

	public BlockchainService(
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

	public void anchorOperation(Long operationId) {
		try {
			if (recordRepository.findByOperationId(operationId).isPresent()) {
				log.warn("Operation {} already has a blockchain record; skipping", operationId);
				return;
			}
			String hash = transactions.execute(status -> hashGenerator.hash(loadOperation(operationId)));
			try {
				AnchorReceipt receipt = ledger.anchor(operationId, hash);
				save(operationId, operation -> BlockchainRecord.anchored(operation, hash, receipt));
			}
			catch (LedgerException ledgerFailure) {
				log.warn("Could not anchor operation {}: {}", operationId, ledgerFailure.getMessage());
				save(operationId, operation -> BlockchainRecord.failed(operation, hash, truncate(ledgerFailure.getMessage())));
			}
		}
		catch (RuntimeException unexpected) {
			log.error("Unexpected error while anchoring operation {}", operationId, unexpected);
		}
	}

	private void save(Long operationId, Function<Operation, BlockchainRecord> factory) {
		transactions.executeWithoutResult(status ->
			recordRepository.save(factory.apply(operationRepository.getReferenceById(operationId))));
	}

	private Operation loadOperation(Long operationId) {
		return operationRepository.findById(operationId)
			.orElseThrow(() -> new IllegalStateException("Operation %d not found".formatted(operationId)));
	}

	private String truncate(String message) {
		return message != null && message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
	}

}
