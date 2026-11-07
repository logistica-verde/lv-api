package br.com.greenvrp.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

public class BesuLedger implements Ledger {

	private static final long RECEIPT_POLL_INTERVAL_MILLIS = 1000;
	private static final int RECEIPT_POLL_ATTEMPTS = 30;

	private final Web3j web3j;
	private final BesuProperties properties;
	private final Object sending = new Object();

	public BesuLedger(Web3j web3j, BesuProperties properties) {
		this.web3j = web3j;
		this.properties = properties;
	}

	@Override
	public AnchorReceipt anchor(long operationId, String hashHex) {
		requireSetting(properties.contractAddress(), "besu.contract-address");
		requireSetting(properties.privateKey(), "besu.private-key");

		Function function = new Function(
			"anchor",
			List.of(new Uint256(BigInteger.valueOf(operationId)), new Bytes32(Numeric.hexStringToByteArray(hashHex))),
			Collections.emptyList());
		try {
			RawTransactionManager transactionManager = new RawTransactionManager(
				web3j, Credentials.create(properties.privateKey()), properties.chainId());
			EthSendTransaction sent;
			synchronized (sending) {
				sent = transactionManager.sendTransaction(
					properties.gasPrice(), properties.gasLimit(), properties.contractAddress(),
					FunctionEncoder.encode(function), BigInteger.ZERO);
			}
			if (sent.hasError()) {
				throw new LedgerException("Besu rejected the anchor transaction: " + sent.getError().getMessage());
			}
			TransactionReceipt receipt = new PollingTransactionReceiptProcessor(
				web3j, RECEIPT_POLL_INTERVAL_MILLIS, RECEIPT_POLL_ATTEMPTS)
				.waitForTransactionReceipt(sent.getTransactionHash());
			if (!receipt.isStatusOK()) {
				throw new LedgerException("Anchor transaction %s was reverted (status %s)"
					.formatted(receipt.getTransactionHash(), receipt.getStatus()));
			}
			return new AnchorReceipt(receipt.getTransactionHash(), receipt.getBlockNumber().longValueExact());
		}
		catch (IOException | TransactionException failure) {
			throw new LedgerException("Could not anchor hash on Besu: " + failure.getMessage(), failure);
		}
	}

	@Override
	public Optional<String> readHash(long operationId) {
		requireSetting(properties.contractAddress(), "besu.contract-address");

		Function function = new Function(
			"getHash",
			List.of(new Uint256(BigInteger.valueOf(operationId))),
			List.of(new TypeReference<Bytes32>() {
			}));
		try {
			EthCall response = web3j.ethCall(
				Transaction.createEthCallTransaction(null, properties.contractAddress(), FunctionEncoder.encode(function)),
				DefaultBlockParameterName.LATEST).send();
			if (response.hasError()) {
				throw new LedgerException("Besu rejected the hash lookup: " + response.getError().getMessage());
			}
			List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
			if (decoded.isEmpty()) {
				throw new LedgerException(
					"No audit contract answered at %s (is it deployed?)".formatted(properties.contractAddress()));
			}
			byte[] stored = ((Bytes32) decoded.get(0)).getValue();
			if (isAllZero(stored)) {
				return Optional.empty();
			}
			return Optional.of(Numeric.toHexStringNoPrefix(stored));
		}
		catch (IOException failure) {
			throw new LedgerException("Could not read hash from Besu: " + failure.getMessage(), failure);
		}
	}

	private void requireSetting(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new LedgerException(name + " is not configured");
		}
	}

	private boolean isAllZero(byte[] bytes) {
		for (byte value : bytes) {
			if (value != 0) {
				return false;
			}
		}
		return true;
	}

}
