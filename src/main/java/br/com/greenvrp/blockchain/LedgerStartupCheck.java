package br.com.greenvrp.blockchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LedgerStartupCheck implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LedgerStartupCheck.class);

	private final BesuProperties properties;

	public LedgerStartupCheck(BesuProperties properties) {
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (properties.contractAddress().isBlank()) {
			log.warn("BESU_CONTRACT_ADDRESS is not set: routes are calculated normally, but their hashes are not "
				+ "anchored and the integrity endpoint answers 503. Deploy the audit contract and set the address.");
		}
		if (properties.privateKey().isBlank()) {
			log.warn("BESU_PRIVATE_KEY is not set: anchoring transactions cannot be signed.");
		}
	}

}
