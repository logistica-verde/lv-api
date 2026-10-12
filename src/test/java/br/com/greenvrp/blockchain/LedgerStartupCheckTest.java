package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LedgerStartupCheckTest {

	private void check(String contractAddress, String privateKey) {
		new LedgerStartupCheck(new BesuProperties(
			new BesuProperties.Rpc("http://localhost:8545"), 1337L, contractAddress, privateKey,
			BigInteger.ZERO, BigInteger.valueOf(300_000))).run(new DefaultApplicationArguments());
	}

	@Test
	void warnsWhenTheContractAddressIsNotSet(CapturedOutput output) {
		check("", "0xabc");

		assertThat(output.getAll()).contains("BESU_CONTRACT_ADDRESS is not set").contains("503");
		assertThat(output.getAll()).doesNotContain("BESU_PRIVATE_KEY is not set");
	}

	@Test
	void warnsWhenThePrivateKeyIsNotSet(CapturedOutput output) {
		check("0x5fbdb2315678afecb367f032d93f642f64180aa3", "");

		assertThat(output.getAll()).contains("BESU_PRIVATE_KEY is not set");
		assertThat(output.getAll()).doesNotContain("BESU_CONTRACT_ADDRESS is not set");
	}

	@Test
	void staysQuietWhenEverythingIsConfigured(CapturedOutput output) {
		check("0x5fbdb2315678afecb367f032d93f642f64180aa3", "0xabc");

		assertThat(output.getAll()).doesNotContain("is not set");
	}

}
