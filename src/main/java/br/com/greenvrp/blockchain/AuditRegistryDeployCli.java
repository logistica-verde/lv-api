package br.com.greenvrp.blockchain;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.Map;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

public final class AuditRegistryDeployCli {

	private AuditRegistryDeployCli() {
	}

	public static void main(String[] arguments) {
		System.exit(run(System.getenv(), System.out, System.err));
	}

	static int run(Map<String, String> environment, PrintStream out, PrintStream err) {
		String rpcUrl = environment.getOrDefault("BESU_RPC_URL", "http://localhost:8545");
		String privateKey = environment.getOrDefault("BESU_PRIVATE_KEY", "");
		if (privateKey.isBlank()) {
			err.println("BESU_PRIVATE_KEY is required: the account that pays for and signs the deployment.");
			return 1;
		}
		BesuProperties properties;
		try {
			properties = new BesuProperties(
				new BesuProperties.Rpc(rpcUrl),
				Long.parseLong(environment.getOrDefault("BESU_CHAIN_ID", "1337")),
				"",
				privateKey,
				new BigInteger(environment.getOrDefault("BESU_GAS_PRICE", "0")),
				AuditRegistryDeployer.DEPLOY_GAS_LIMIT);
		}
		catch (NumberFormatException invalid) {
			err.println("BESU_CHAIN_ID and BESU_GAS_PRICE must be numbers.");
			return 1;
		}

		Web3j web3j = Web3j.build(new HttpService(rpcUrl));
		try {
			String address = new AuditRegistryDeployer(web3j, properties).deploy();
			out.println("BESU_CONTRACT_ADDRESS=" + address);
			return 0;
		}
		catch (LedgerException failure) {
			err.println("Deployment failed: " + failure.getMessage());
			return 1;
		}
		finally {
			web3j.shutdown();
		}
	}

}
