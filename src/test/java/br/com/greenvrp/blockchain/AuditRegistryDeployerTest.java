package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.protocol.Web3j;
import org.web3j.utils.Numeric;

class AuditRegistryDeployerTest {

	private static final String DEPLOYED_AT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";

	private FakeBesuServer besu;
	private Web3j web3j;
	private Credentials credentials;
	private String privateKey;

	@BeforeEach
	void startFakeBesu() throws Exception {
		besu = new FakeBesuServer();
		web3j = besu.web3j();
		ECKeyPair keyPair = Keys.createEcKeyPair();
		credentials = Credentials.create(keyPair);
		privateKey = Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);
	}

	@AfterEach
	void stopFakeBesu() {
		web3j.shutdown();
		besu.close();
	}

	private AuditRegistryDeployer deployer(String key) {
		return new AuditRegistryDeployer(web3j, new BesuProperties(
			new BesuProperties.Rpc("unused"), 1337L, "", key, BigInteger.ZERO, BigInteger.valueOf(300_000)));
	}

	private AtomicReference<String> acceptDeployment(String status, String contractAddress) {
		AtomicReference<String> rawTransaction = new AtomicReference<>();
		besu.on("eth_getTransactionCount", body -> FakeBesuServer.result("\"0x0\""));
		besu.on("eth_sendRawTransaction", body -> {
			rawTransaction.set(FakeBesuServer.firstParam(body));
			return FakeBesuServer.result("\"" + Hash.sha3(rawTransaction.get()) + "\"");
		});
		besu.on("eth_getTransactionReceipt",
			body -> FakeBesuServer.receipt(Hash.sha3(rawTransaction.get()), status, contractAddress));
		return rawTransaction;
	}

	@Test
	void sendsAContractCreationWithTheCompiledBytecodeAndReturnsTheNewAddress() throws Exception {
		AtomicReference<String> rawTransaction = acceptDeployment("0x1", DEPLOYED_AT);

		String address = deployer(privateKey).deploy();

		assertThat(address).isEqualTo(DEPLOYED_AT);
		RawTransaction decoded = TransactionDecoder.decode(rawTransaction.get());
		assertThat(Numeric.cleanHexPrefix(decoded.getTo())).isEmpty();
		assertThat(Numeric.cleanHexPrefix(decoded.getData())).isEqualTo(AuditRegistryDeployer.bytecode());
		assertThat(decoded.getGasPrice()).isEqualTo(BigInteger.ZERO);
		assertThat(decoded.getGasLimit()).isEqualTo(AuditRegistryDeployer.DEPLOY_GAS_LIMIT);
		assertThat(decoded.getValue()).isEqualTo(BigInteger.ZERO);
		SignedRawTransaction signed = (SignedRawTransaction) decoded;
		assertThat(signed.getFrom()).isEqualToIgnoringCase(credentials.getAddress());
		assertThat(signed.getChainId()).isEqualTo(1337L);
	}

	@Test
	void failsWhenTheDeploymentIsReverted() {
		acceptDeployment("0x0", null);

		assertThatThrownBy(() -> deployer(privateKey).deploy())
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("reverted");
	}

	@Test
	void failsWhenTheReceiptHasNoContractAddress() {
		acceptDeployment("0x1", null);

		assertThatThrownBy(() -> deployer(privateKey).deploy())
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("did not create a contract");
	}

	@Test
	void failsWhenBesuRejectsTheTransaction() {
		besu.on("eth_getTransactionCount", body -> FakeBesuServer.result("\"0x0\""));
		besu.on("eth_sendRawTransaction", body -> FakeBesuServer.error("insufficient funds"));

		assertThatThrownBy(() -> deployer(privateKey).deploy())
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("insufficient funds");
	}

	@Test
	void failsWhenBesuIsUnreachable() {
		besu.stopAccepting();

		assertThatThrownBy(() -> deployer(privateKey).deploy()).isInstanceOf(LedgerException.class);
	}

	@Test
	void refusesToDeployWithoutAPrivateKey() {
		assertThatThrownBy(() -> deployer("").deploy())
			.isInstanceOf(LedgerException.class)
			.hasMessage("besu.private-key is not configured");
	}

}
