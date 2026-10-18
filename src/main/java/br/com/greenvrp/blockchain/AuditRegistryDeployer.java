package br.com.greenvrp.blockchain;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;

public class AuditRegistryDeployer {

	static final String BYTECODE_RESOURCE = "/contracts/AuditRegistry.bin";
	static final BigInteger DEPLOY_GAS_LIMIT = BigInteger.valueOf(1_500_000);

	private static final long RECEIPT_POLL_INTERVAL_MILLIS = 1000;
	private static final int RECEIPT_POLL_ATTEMPTS = 60;

	private final Web3j web3j;
	private final BesuProperties properties;

	public AuditRegistryDeployer(Web3j web3j, BesuProperties properties) {
		this.web3j = web3j;
		this.properties = properties;
	}

	public String deploy() {
		if (properties.privateKey() == null || properties.privateKey().isBlank()) {
			throw new LedgerException("besu.private-key is not configured");
		}
		try {
			RawTransactionManager transactionManager = new RawTransactionManager(
				web3j, Credentials.create(properties.privateKey()), properties.chainId());
			EthSendTransaction sent = transactionManager.sendTransaction(
				properties.gasPrice(), DEPLOY_GAS_LIMIT, "", "0x" + bytecode(), BigInteger.ZERO);
			if (sent.hasError()) {
				throw new LedgerException("Besu rejected the deployment transaction: " + sent.getError().getMessage());
			}
			TransactionReceipt receipt = new PollingTransactionReceiptProcessor(
				web3j, RECEIPT_POLL_INTERVAL_MILLIS, RECEIPT_POLL_ATTEMPTS)
				.waitForTransactionReceipt(sent.getTransactionHash());
			if (!receipt.isStatusOK()) {
				throw new LedgerException("Deployment transaction %s was reverted (status %s)"
					.formatted(receipt.getTransactionHash(), receipt.getStatus()));
			}
			String address = receipt.getContractAddress();
			if (address == null || address.isBlank()) {
				throw new LedgerException(
					"Deployment transaction %s did not create a contract".formatted(receipt.getTransactionHash()));
			}
			return address;
		}
		catch (IOException | TransactionException failure) {
			throw new LedgerException("Could not deploy the audit contract on Besu: " + failure.getMessage(), failure);
		}
	}

	static String bytecode() throws IOException {
		try (InputStream stream = AuditRegistryDeployer.class.getResourceAsStream(BYTECODE_RESOURCE)) {
			if (stream == null) {
				throw new IOException("Missing resource " + BYTECODE_RESOURCE);
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
		}
	}

}
