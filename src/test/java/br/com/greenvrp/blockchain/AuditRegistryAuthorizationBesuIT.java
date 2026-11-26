package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

@EnabledIfEnvironmentVariable(named = AuditRegistryAuthorizationBesuIT.RPC_URL, matches = "https?://.+")
class AuditRegistryAuthorizationBesuIT {

	static final String RPC_URL = "LV_BESU_IT_RPC_URL";
	static final String EVIDENCE = "LV_BESU_IT_EVIDENCE";

	private static final BigInteger GAS_LIMIT = BigInteger.valueOf(300_000);
	private static final String ERROR_SELECTOR = "08c379a0";

	private Web3j web3j;
	private long chainId;
	private Credentials owner;
	private Credentials stranger;
	private String contract;
	private final Map<String, Object> evidence = new LinkedHashMap<>();

	@BeforeEach
	void deployTheCommittedBytecodeWithAThrowawayOwner() throws Exception {
		web3j = Web3j.build(new HttpService(System.getenv(RPC_URL)));
		chainId = web3j.ethChainId().send().getChainId().longValueExact();
		owner = Credentials.create(Keys.createEcKeyPair());
		stranger = Credentials.create(Keys.createEcKeyPair());

		TransactionReceipt deployment = mined(new RawTransactionManager(web3j, owner, chainId).sendTransaction(
			BigInteger.ZERO, AuditRegistryDeployer.DEPLOY_GAS_LIMIT, "", "0x" + AuditRegistryDeployer.bytecode(),
			BigInteger.ZERO));
		assertThat(deployment.isStatusOK()).isTrue();
		contract = deployment.getContractAddress();

		evidence.put("chainId", chainId);
		evidence.put("contract", contract);
		evidence.put("owner", owner.getAddress());
		evidence.put("stranger", stranger.getAddress());
		evidence.put("deployment", receiptOf(deployment));
		evidence.put("runtimeCode", web3j.ethGetCode(contract, DefaultBlockParameterName.LATEST).send().getCode());
	}

	@AfterEach
	void saveTheEvidence() throws Exception {
		String path = System.getenv(EVIDENCE);
		if (path != null && !path.isBlank()) {
			Files.writeString(Path.of(path), json(evidence), StandardCharsets.UTF_8);
		}
		web3j.shutdown();
	}

	@Test
	void onlyTheOwnerAnchorsAndEachOperationOnlyOnce() throws Exception {
		BigInteger operation = BigInteger.valueOf(System.currentTimeMillis());
		String first = "%064x".formatted(operation);
		String second = "%064x".formatted(operation.add(BigInteger.ONE));
		BigInteger other = operation.add(BigInteger.TWO);

		Map<String, Object> authorized = attempt("autorizada", owner, operation, first);
		Map<String, Object> repeated = attempt("segunda gravação da mesma operação", owner, operation, second);
		Map<String, Object> unauthorized = attempt("conta sem permissão", stranger, other, first);

		evidence.put("attempts", List.of(authorized, repeated, unauthorized));
		evidence.put("hashAfter", Map.of(operation.toString(), storedHash(operation), other.toString(), storedHash(other)));

		assertThat(authorized).containsEntry("status", "0x1").containsEntry("simulatedRevert", null);
		assertThat(repeated).containsEntry("status", "0x0").containsEntry("simulatedRevert", "already anchored");
		assertThat(unauthorized).containsEntry("status", "0x0").containsEntry("simulatedRevert", "not the owner");
		assertThat(storedHash(operation)).isEqualTo(first);
		assertThat(storedHash(other)).isEqualTo("0".repeat(64));
	}

	private Map<String, Object> attempt(String label, Credentials from, BigInteger operation, String hash) throws Exception {
		String data = FunctionEncoder.encode(new Function(
			"anchor", List.of(new Uint256(operation), new Bytes32(Numeric.hexStringToByteArray(hash))), List.of()));
		EthCall simulated = web3j.ethCall(
			Transaction.createEthCallTransaction(from.getAddress(), contract, data), DefaultBlockParameterName.LATEST).send();
		TransactionReceipt receipt = mined(new RawTransactionManager(web3j, from, chainId)
			.sendTransaction(BigInteger.ZERO, GAS_LIMIT, contract, data, BigInteger.ZERO));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("case", label);
		result.put("from", from.getAddress());
		result.put("operationId", operation.toString());
		result.put("hash", hash);
		result.put("simulatedRevert", revertReason(simulated));
		result.putAll(receiptOf(receipt));
		return result;
	}

	private TransactionReceipt mined(EthSendTransaction sent) throws Exception {
		assertThat(sent.hasError()).as(sent.hasError() ? sent.getError().getMessage() : "").isFalse();
		return new PollingTransactionReceiptProcessor(web3j, 500, 60).waitForTransactionReceipt(sent.getTransactionHash());
	}

	private String storedHash(BigInteger operation) throws Exception {
		Function getHash = new Function("getHash", List.of(new Uint256(operation)), List.of(new TypeReference<Bytes32>() {
		}));
		EthCall call = web3j.ethCall(Transaction.createEthCallTransaction(null, contract, FunctionEncoder.encode(getHash)),
			DefaultBlockParameterName.LATEST).send();
		List<Type> decoded = FunctionReturnDecoder.decode(call.getValue(), getHash.getOutputParameters());
		return Numeric.toHexStringNoPrefix(((Bytes32) decoded.get(0)).getValue());
	}

	private static Map<String, Object> receiptOf(TransactionReceipt receipt) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("transactionHash", receipt.getTransactionHash());
		result.put("blockNumber", receipt.getBlockNumber().longValueExact());
		result.put("status", receipt.getStatus());
		result.put("gasUsed", receipt.getGasUsed().longValueExact());
		result.put("logs", receipt.getLogs().size());
		return result;
	}

	private static String revertReason(EthCall call) {
		String data = call.hasError() && call.getError().getData() != null ? call.getError().getData().toString()
			: call.getRevertReason();
		if (data == null) {
			return null;
		}
		String hex = Numeric.cleanHexPrefix(data.replace("\"", ""));
		if (!hex.startsWith(ERROR_SELECTOR)) {
			return data;
		}
		byte[] payload = Numeric.hexStringToByteArray(hex.substring(8));
		int length = new BigInteger(1, java.util.Arrays.copyOfRange(payload, 32, 64)).intValueExact();
		return new String(payload, 64, length, StandardCharsets.UTF_8);
	}

	private static String json(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof Map<?, ?> map) {
			List<String> fields = new ArrayList<>();
			map.forEach((key, item) -> fields.add("\"%s\": %s".formatted(key, json(item))));
			return "{" + String.join(", ", fields) + "}";
		}
		if (value instanceof List<?> list) {
			return "[" + String.join(", ", list.stream().map(AuditRegistryAuthorizationBesuIT::json).toList()) + "]";
		}
		if (value instanceof Number) {
			return value.toString();
		}
		return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

}
