package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

class AuditRegistryDeployCliTest {

	private static final String DEPLOYED_AT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";

	private final ByteArrayOutputStream out = new ByteArrayOutputStream();
	private final ByteArrayOutputStream err = new ByteArrayOutputStream();

	private int run(Map<String, String> environment) {
		return AuditRegistryDeployCli.run(
			environment, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
	}

	private String newPrivateKey() throws Exception {
		ECKeyPair keyPair = Keys.createEcKeyPair();
		return Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);
	}

	@Test
	void deploysAndPrintsTheLineToCopyIntoTheConfiguration() throws Exception {
		try (FakeBesuServer besu = new FakeBesuServer()) {
			AtomicReference<String> rawTransaction = new AtomicReference<>();
			besu.on("eth_getTransactionCount", body -> FakeBesuServer.result("\"0x0\""));
			besu.on("eth_sendRawTransaction", body -> {
				rawTransaction.set(FakeBesuServer.firstParam(body));
				return FakeBesuServer.result("\"" + Hash.sha3(rawTransaction.get()) + "\"");
			});
			besu.on("eth_getTransactionReceipt",
				body -> FakeBesuServer.receipt(Hash.sha3(rawTransaction.get()), "0x1", DEPLOYED_AT));
			Map<String, String> environment = new HashMap<>();
			environment.put("BESU_RPC_URL", besu.url());
			environment.put("BESU_PRIVATE_KEY", newPrivateKey());

			int exitCode = run(environment);

			assertThat(exitCode).isZero();
			assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("BESU_CONTRACT_ADDRESS=" + DEPLOYED_AT + System.lineSeparator());
			assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
		}
	}

	@Test
	void explainsWhenThePrivateKeyIsMissing() {
		int exitCode = run(Map.of());

		assertThat(exitCode).isEqualTo(1);
		assertThat(err.toString(StandardCharsets.UTF_8)).contains("BESU_PRIVATE_KEY is required");
		assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
	}

	@Test
	void explainsWhenTheNumbersAreInvalid() throws Exception {
		int exitCode = run(Map.of("BESU_PRIVATE_KEY", newPrivateKey(), "BESU_CHAIN_ID", "abc"));

		assertThat(exitCode).isEqualTo(1);
		assertThat(err.toString(StandardCharsets.UTF_8)).contains("must be numbers");
	}

	@Test
	void reportsAFailedDeploymentWithoutPrintingAnAddress() throws Exception {
		try (FakeBesuServer besu = new FakeBesuServer()) {
			besu.on("eth_getTransactionCount", body -> FakeBesuServer.result("\"0x0\""));
			besu.on("eth_sendRawTransaction", body -> FakeBesuServer.error("insufficient funds"));
			Map<String, String> environment = new HashMap<>();
			environment.put("BESU_RPC_URL", besu.url());
			environment.put("BESU_PRIVATE_KEY", newPrivateKey());

			int exitCode = run(environment);

			assertThat(exitCode).isEqualTo(1);
			assertThat(err.toString(StandardCharsets.UTF_8)).contains("Deployment failed").contains("insufficient funds");
			assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
		}
	}

}
