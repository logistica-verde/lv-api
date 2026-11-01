package br.com.greenvrp.blockchain;

import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.OperationRepository;
import java.util.List;
import java.util.Optional;
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
	private final AnchorAttemptRepository attemptRepository;
	private final HashGenerator hashGenerator;
	private final Ledger ledger;
	private final TransactionTemplate transactions;

	public BlockchainService(
		OperationRepository operationRepository,
		BlockchainRecordRepository recordRepository,
		AnchorAttemptRepository attemptRepository,
		HashGenerator hashGenerator,
		Ledger ledger,
		PlatformTransactionManager transactionManager) {
		this.operationRepository = operationRepository;
		this.recordRepository = recordRepository;
		this.attemptRepository = attemptRepository;
		this.hashGenerator = hashGenerator;
		this.ledger = ledger;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	public void anchorOperation(Long operationId) {
		try {
			Optional<BlockchainRecord> existing = recordRepository.findByOperationId(operationId);
			if (existing.map(record -> record.getStatus() == AnchorStatus.ANCHORED).orElse(false)) {
				log.warn("Operation {} already has a blockchain record; skipping", operationId);
				return;
			}
			String hash = transactions.execute(status -> hashGenerator.hash(loadOperation(operationId)));
			try {
				if (isAlreadyOnLedger(operationId, hash)) {
					log.info("Operation {} is already on the ledger with the same hash; recovering the record", operationId);
					settle(operationId, hash, AnchorAttemptResult.RECOVERED, null, null, null);
					return;
				}
				AnchorReceipt receipt = ledger.anchor(operationId, hash);
				settle(operationId, hash, AnchorAttemptResult.ANCHORED, receipt.txHash(), receipt.blockNumber(), null);
			}
			catch (LedgerException ledgerFailure) {
				log.warn("Could not anchor operation {}: {}", operationId, ledgerFailure.getMessage());
				settle(operationId, hash, AnchorAttemptResult.FAILED, null, null, truncate(ledgerFailure.getMessage()));
			}
		}
		catch (RuntimeException unexpected) {
			log.error("Unexpected error while anchoring operation {}", operationId, unexpected);
		}
	}

	public int retryFailed() {
		List<Long> operationIds = transactions.execute(status ->
			recordRepository.findByStatus(AnchorStatus.FAILED).stream()
				.map(record -> record.getOperation().getId())
				.toList());
		operationIds.forEach(this::anchorOperation);
		return operationIds.size();
	}

	private boolean isAlreadyOnLedger(Long operationId, String hash) {
		try {
			return ledger.readHash(operationId).filter(hash::equalsIgnoreCase).isPresent();
		}
		catch (LedgerException unreachable) {
			return false;
		}
	}

	private void settle(
		Long operationId, String hash, AnchorAttemptResult result, String txHash, Long blockNumber, String message) {
		transactions.executeWithoutResult(status -> {
			int number = attemptRepository.countByOperationId(operationId) + 1;
			attemptRepository.save(new AnchorAttempt(operationId, number, result, txHash, message));
			boolean failed = result == AnchorAttemptResult.FAILED;
			Optional<BlockchainRecord> existing = recordRepository.findByOperationId(operationId);
			if (existing.isPresent()) {
				if (failed) {
					existing.get().markFailed(hash, message);
				}
				else {
					existing.get().markAnchored(hash, txHash, blockNumber);
				}
				return;
			}
			Operation operation = operationRepository.getReferenceById(operationId);
			BlockchainRecord created = BlockchainRecord.failed(operation, hash, message);
			if (!failed) {
				created.markAnchored(hash, txHash, blockNumber);
			}
			recordRepository.save(created);
		});
	}

	private Operation loadOperation(Long operationId) {
		return operationRepository.findById(operationId)
			.orElseThrow(() -> new IllegalStateException("Operation %d not found".formatted(operationId)));
	}

	private String truncate(String message) {
		return message != null && message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
	}

}
