package br.com.greenvrp.blockchain;

import java.util.Optional;

public interface Ledger {

	AnchorReceipt anchor(long operationId, String hashHex);

	Optional<String> readHash(long operationId);

	String contractAddress();

}
