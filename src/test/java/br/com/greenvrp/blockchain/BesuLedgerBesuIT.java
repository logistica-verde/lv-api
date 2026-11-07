package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

@EnabledIfEnvironmentVariable(named = BesuLedgerBesuIT.RPC_URL, matches = "https?://.+")
class BesuLedgerBesuIT {

	static final String RPC_URL = "LV_BESU_IT_RPC_URL";

	private Web3j web3j;
	private BesuLedger ledger;
	private long firstOperation;

	@BeforeEach
	void deployAFreshContract() throws Exception {
		String url = System.getenv(RPC_URL);
		web3j = Web3j.build(new HttpService(url));
		String key = Numeric.toHexStringNoPrefixZeroPadded(Keys.createEcKeyPair().getPrivateKey(), 64);
		long chainId = web3j.ethChainId().send().getChainId().longValueExact();
		BesuProperties deploying = new BesuProperties(
			new BesuProperties.Rpc(url), chainId, "", key, BigInteger.ZERO, BigInteger.valueOf(300_000));
		String contract = new AuditRegistryDeployer(web3j, deploying).deploy();
		ledger = new BesuLedger(web3j, new BesuProperties(
			new BesuProperties.Rpc(url), chainId, contract, key, BigInteger.ZERO, BigInteger.valueOf(300_000)));
		firstOperation = System.currentTimeMillis();
	}

	@AfterEach
	void disconnect() {
		web3j.shutdown();
	}

	private record Outcome(List<AnchorReceipt> anchored, List<String> failures) {
	}

	private Outcome anchorAtOnce(int count) throws InterruptedException {
		ExecutorService threads = Executors.newFixedThreadPool(count);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<AnchorReceipt>> anchors = new ArrayList<>();
			for (int index = 0; index < count; index++) {
				long operationId = firstOperation + index;
				anchors.add(threads.submit(() -> {
					start.await();
					return ledger.anchor(operationId, "%064x".formatted(operationId));
				}));
			}
			start.countDown();
			List<AnchorReceipt> anchored = new ArrayList<>();
			List<String> failures = new ArrayList<>();
			for (Future<AnchorReceipt> anchor : anchors) {
				try {
					anchored.add(anchor.get(60, TimeUnit.SECONDS));
				}
				catch (ExecutionException failure) {
					failures.add(failure.getCause().getMessage());
				}
				catch (java.util.concurrent.TimeoutException slow) {
					failures.add("no answer in 60 s");
				}
			}
			return new Outcome(anchored, failures);
		}
		finally {
			threads.shutdownNow();
		}
	}

	@ParameterizedTest(name = "{0} simultaneous anchors")
	@ValueSource(ints = {2, 10})
	void everySimultaneousAnchorReachesABlock(int count) throws Exception {
		Outcome outcome = anchorAtOnce(count);

		System.out.printf("[besu-it] %d simultaneous: %d anchored, %d failed %s%n",
			count, outcome.anchored().size(), outcome.failures().size(), outcome.failures());
		assertThat(outcome.failures()).isEmpty();
		assertThat(outcome.anchored()).extracting(AnchorReceipt::txHash).doesNotHaveDuplicates().hasSize(count);
		for (int index = 0; index < count; index++) {
			long operationId = firstOperation + index;
			assertThat(ledger.readHash(operationId)).contains("%064x".formatted(operationId));
		}
	}

	@Test
	void anchorsOneAtATimeAsBefore() throws Exception {
		AnchorReceipt receipt = ledger.anchor(firstOperation, "%064x".formatted(firstOperation));

		assertThat(receipt.blockNumber()).isPositive();
	}

}
