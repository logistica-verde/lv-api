package br.com.greenvrp.blockchain;

import static br.com.greenvrp.blockchain.FakeBesuServer.firstParam;
import static br.com.greenvrp.blockchain.FakeBesuServer.receipt;
import static br.com.greenvrp.blockchain.FakeBesuServer.result;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.utils.Numeric;

class BesuLedgerConcurrencyTest {

	private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
	private static final int SIMULTANEOUS = 10;
	private static final long NODE_LATENCY_MILLIS = 30;

	private static final class TransactionPool {

		private final Map<BigInteger, String> byNonce = new ConcurrentHashMap<>();
		private final Set<String> replaced = ConcurrentHashMap.newKeySet();

		void serve(FakeBesuServer node) {
			node.on("eth_getTransactionCount", body -> {
				pause();
				return result("\"0x" + Integer.toHexString(byNonce.size()) + "\"");
			});
			node.on("eth_sendRawTransaction", body -> {
				String raw = firstParam(body);
				String hash = Hash.sha3(raw);
				String previous = byNonce.put(TransactionDecoder.decode(raw).getNonce(), hash);
				if (previous != null) {
					replaced.add(previous);
				}
				return result("\"" + hash + "\"");
			});
			node.on("eth_getTransactionReceipt", body -> {
				String hash = firstParam(body);
				return byNonce.containsValue(hash) ? receipt(hash, "0x1", null) : result("null");
			});
		}

		private static void pause() {
			try {
				Thread.sleep(NODE_LATENCY_MILLIS);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
		}

	}

	@Test
	void simultaneousAnchorsGetDistinctNoncesSoNoTransactionReplacesAnother() throws Exception {
		TransactionPool pool = new TransactionPool();
		ExecutorService threads = Executors.newFixedThreadPool(SIMULTANEOUS);
		try (FakeBesuServer node = new FakeBesuServer()) {
			pool.serve(node);
			String key = Numeric.toHexStringNoPrefixZeroPadded(Keys.createEcKeyPair().getPrivateKey(), 64);
			BesuLedger ledger = new BesuLedger(node.web3j(), new BesuProperties(
				new BesuProperties.Rpc(node.url()), 1337L, CONTRACT, key, BigInteger.ZERO, BigInteger.valueOf(300_000)));
			CountDownLatch start = new CountDownLatch(1);
			List<Future<AnchorReceipt>> anchors = new ArrayList<>();
			for (int operation = 1; operation <= SIMULTANEOUS; operation++) {
				long operationId = operation;
				anchors.add(threads.submit(() -> {
					start.await();
					return ledger.anchor(operationId, "%064x".formatted(operationId));
				}));
			}

			start.countDown();
			List<String> confirmed = new ArrayList<>();
			for (Future<AnchorReceipt> anchor : anchors) {
				confirmed.add(anchor.get(10, TimeUnit.SECONDS).txHash());
			}

			assertThat(pool.replaced).isEmpty();
			assertThat(confirmed).doesNotHaveDuplicates().hasSize(SIMULTANEOUS);
			assertThat(pool.byNonce.keySet())
				.containsExactlyInAnyOrderElementsOf(
					java.util.stream.IntStream.range(0, SIMULTANEOUS).mapToObj(BigInteger::valueOf).toList());
		}
		finally {
			threads.shutdownNow();
		}
	}

}
