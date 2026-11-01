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
			if (existing.map(record -> record.getStatus() != AnchorStatus.FAILED).orElse(false)) {
				log.warn("Operation {} already has a {} blockchain record; skipping", operationId, existing.get().getStatus());
				return;
			}
			String hash = transactions.execute(status -> hashGenerator.hash(loadOperation(operationId)));
			try {
				Optional<String> onLedger = readLedger(operationId);
				if (onLedger.isPresent() && onLedger.get().equalsIgnoreCase(hash)) {
					log.info("Operation {} is already on the ledger with the same hash; recovering the record", operationId);
					settle(operationId, hash, AnchorAttemptResult.RECOVERED, null, null, null);
					return;
				}
				if (onLedger.isPresent()) {
					boolean sentEarlier = existing.map(BlockchainRecord::getHashSha256)
						.filter(onLedger.get()::equalsIgnoreCase)
						.isPresent();
					String message = sentEarlier
						? "The ledger holds the hash this API sent on an earlier attempt (%s); the operation changed since then"
							.formatted(onLedger.get())
						: "The ledger holds a hash this API never sent for this operation: " + onLedger.get();
					log.error("Operation {} diverges from the ledger: the ledger holds {} but the database hashes to {}",
						operationId, onLedger.get(), hash);
					settle(operationId, hash, AnchorAttemptResult.DIVERGENT, null, null, message);
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

	private Optional<String> readLedger(Long operationId) {
		try {
			return ledger.readHash(operationId);
		}
		catch (LedgerException unreachable) {
			return Optional.empty();
		}
	}

	private void settle(
		Long operationId, String hash, AnchorAttemptResult result, String txHash, Long blockNumber, String message) {
		transactions.executeWithoutResult(status -> {
			int number = attemptRepository.countByOperationId(operationId) + 1;
			attemptRepository.save(new AnchorAttempt(operationId, number, result, txHash, message));
			BlockchainRecord record = recordRepository.findByOperationId(operationId)
				.orElseGet(() -> recordRepository.save(
					BlockchainRecord.failed(operationRepository.getReferenceById(operationId), hash, message)));
			switch (result) {
				case ANCHORED, RECOVERED -> record.markAnchored(hash, txHash, blockNumber);
				case DIVERGENT -> record.markDivergent(hash, message);
				case FAILED -> record.markFailed(hash, message);
			}
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
