package br.com.greenvrp.blockchain;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryLedger implements Ledger {

	private final Map<Long, String> hashes = new ConcurrentHashMap<>();
	private final AtomicLong blockCounter = new AtomicLong(100);
	private volatile RuntimeException failure;

	@Override
	public AnchorReceipt anchor(long operationId, String hashHex) {
		failIfRequested();
		if (hashes.putIfAbsent(operationId, hashHex) != null) {
			throw new LedgerException("already anchored");
		}
		return new AnchorReceipt("0x%064x".formatted(operationId), blockCounter.incrementAndGet());
	}

	@Override
	public Optional<String> readHash(long operationId) {
		failIfRequested();
		return Optional.ofNullable(hashes.get(operationId));
	}

	public void failWith(RuntimeException exception) {
		this.failure = exception;
	}

	public void recover() {
		this.failure = null;
	}

	public void clear() {
		hashes.clear();
		recover();
	}

	private void failIfRequested() {
		if (failure != null) {
			throw failure;
		}
	}

}
