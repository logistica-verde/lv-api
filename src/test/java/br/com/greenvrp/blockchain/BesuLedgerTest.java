package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

class BesuLedgerTest {

	private static final String CONTRACT = "0x5fbdb2315678afecb367f032d93f642f64180aa3";
	private static final String HASH = "ab".repeat(32);
	private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

	private final Map<String, Function<String, String>> rpcHandlers = new ConcurrentHashMap<>();
	private final AtomicReference<String> lastCallBody = new AtomicReference<>();
	private HttpServer server;
	private Web3j web3j;
	private Credentials credentials;
	private String privateKey;

	@BeforeEach
	void startFakeBesu() throws Exception {
		ECKeyPair keyPair = Keys.createEcKeyPair();
		credentials = Credentials.create(keyPair);
		privateKey = Numeric.toHexStringNoPrefixZeroPadded(keyPair.getPrivateKey(), 64);

		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Matcher method = METHOD.matcher(body);
			Matcher id = ID.matcher(body);
			String methodName = method.find() ? method.group(1) : "";
			String requestId = id.find() ? id.group(1) : "1";
			if ("eth_call".equals(methodName)) {
				lastCallBody.set(body);
			}
			Function<String, String> handler = rpcHandlers.get(methodName);
			String payload = handler == null
				? "{\"jsonrpc\":\"2.0\",\"id\":" + requestId
					+ ",\"error\":{\"code\":-32601,\"message\":\"unexpected method " + methodName + "\"}}"
				: handler.apply(body).replace("\"id\":0", "\"id\":" + requestId);
			byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		web3j = Web3j.build(new HttpService("http://localhost:" + server.getAddress().getPort()));
	}

	@AfterEach
	void stopFakeBesu() {
		web3j.shutdown();
		server.stop(0);
	}

	private BesuLedger ledger(String contractAddress, String key) {
		return new BesuLedger(web3j, new BesuProperties(
			new BesuProperties.Rpc("unused"), 1337L, contractAddress, key, BigInteger.ZERO, BigInteger.valueOf(300_000)));
	}

	private String result(String jsonValue) {
		return "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":" + jsonValue + "}";
	}

	private String receipt(String txHash, String status) {
		return result("{\"transactionHash\":\"" + txHash + "\",\"transactionIndex\":\"0x0\","
			+ "\"blockHash\":\"0x" + "11".repeat(32) + "\",\"blockNumber\":\"0x2a\","
			+ "\"cumulativeGasUsed\":\"0x5208\",\"gasUsed\":\"0x5208\",\"status\":\"" + status + "\","
			+ "\"logs\":[],\"logsBloom\":\"0x00\"}");
	}

	@Test
	void anchorSendsSignedTransactionCallingAnchorAndReturnsReceipt() throws Exception {
		AtomicReference<String> rawTransaction = new AtomicReference<>();
		rpcHandlers.put("eth_getTransactionCount", body -> result("\"0x0\""));
		rpcHandlers.put("eth_sendRawTransaction", body -> {
			Matcher raw = Pattern.compile("\"params\"\\s*:\\s*\\[\\s*\"(0x[0-9a-fA-F]+)\"").matcher(body);
			assertThat(raw.find()).isTrue();
			rawTransaction.set(raw.group(1));
			return result("\"" + Hash.sha3(raw.group(1)) + "\"");
		});
		rpcHandlers.put("eth_getTransactionReceipt", body -> receipt(Hash.sha3(rawTransaction.get()), "0x1"));

		AnchorReceipt anchored = ledger(CONTRACT, privateKey).anchor(7, HASH);

		assertThat(anchored.txHash()).isEqualTo(Hash.sha3(rawTransaction.get()));
		assertThat(anchored.blockNumber()).isEqualTo(42L);

		RawTransaction decoded = TransactionDecoder.decode(rawTransaction.get());
		assertThat(decoded.getTo()).isEqualToIgnoringCase(CONTRACT);
		assertThat(decoded.getGasPrice()).isEqualTo(BigInteger.ZERO);
		String expectedData = FunctionEncoder.encode(new org.web3j.abi.datatypes.Function(
			"anchor",
			List.of(new Uint256(BigInteger.valueOf(7)), new Bytes32(Numeric.hexStringToByteArray(HASH))),
			Collections.emptyList()));
		assertThat(Numeric.cleanHexPrefix(decoded.getData())).isEqualTo(Numeric.cleanHexPrefix(expectedData));
		assertThat(((SignedRawTransaction) decoded).getFrom()).isEqualToIgnoringCase(credentials.getAddress());
	}

	@Test
	void anchorFailsWhenTheTransactionIsReverted() {
		AtomicReference<String> rawTransaction = new AtomicReference<>();
		rpcHandlers.put("eth_getTransactionCount", body -> result("\"0x0\""));
		rpcHandlers.put("eth_sendRawTransaction", body -> {
			Matcher raw = Pattern.compile("\"params\"\\s*:\\s*\\[\\s*\"(0x[0-9a-fA-F]+)\"").matcher(body);
			raw.find();
			rawTransaction.set(raw.group(1));
			return result("\"" + Hash.sha3(raw.group(1)) + "\"");
		});
		rpcHandlers.put("eth_getTransactionReceipt", body -> receipt(Hash.sha3(rawTransaction.get()), "0x0"));

		assertThatThrownBy(() -> ledger(CONTRACT, privateKey).anchor(7, HASH))
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("reverted");
	}

	@Test
	void anchorFailsWhenBesuRejectsTheTransaction() {
		rpcHandlers.put("eth_getTransactionCount", body -> result("\"0x0\""));
		rpcHandlers.put("eth_sendRawTransaction", body ->
			"{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{\"code\":-32000,\"message\":\"insufficient funds\"}}");

		assertThatThrownBy(() -> ledger(CONTRACT, privateKey).anchor(7, HASH))
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("insufficient funds");
	}

	@Test
	void anchorFailsWhenBesuIsUnreachable() {
		server.stop(0);

		assertThatThrownBy(() -> ledger(CONTRACT, privateKey).anchor(7, HASH))
			.isInstanceOf(LedgerException.class);
	}

	@Test
	void readHashReturnsTheStoredHash() {
		rpcHandlers.put("eth_call", body -> result("\"0x" + HASH + "\""));

		Optional<String> stored = ledger(CONTRACT, privateKey).readHash(7);

		assertThat(stored).contains(HASH);
		String expectedData = FunctionEncoder.encode(new org.web3j.abi.datatypes.Function(
			"getHash",
			List.of(new Uint256(BigInteger.valueOf(7))),
			List.of(new TypeReference<Bytes32>() {
			})));
		assertThat(lastCallBody.get())
			.containsIgnoringCase(CONTRACT)
			.containsIgnoringCase(expectedData);
	}

	@Test
	void readHashReturnsEmptyWhenNothingWasAnchored() {
		rpcHandlers.put("eth_call", body -> result("\"0x" + "00".repeat(32) + "\""));

		assertThat(ledger(CONTRACT, privateKey).readHash(7)).isEmpty();
	}

	@Test
	void readHashFailsWhenNoContractIsDeployedAtTheAddress() {
		rpcHandlers.put("eth_call", body -> result("\"0x\""));

		assertThatThrownBy(() -> ledger(CONTRACT, privateKey).readHash(7))
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("deployed");
	}

	@Test
	void readHashFailsWhenBesuReturnsAnError() {
		rpcHandlers.put("eth_call", body ->
			"{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{\"code\":-32000,\"message\":\"execution reverted\"}}");

		assertThatThrownBy(() -> ledger(CONTRACT, privateKey).readHash(7))
			.isInstanceOf(LedgerException.class)
			.hasMessageContaining("execution reverted");
	}

	@Test
	void requiresContractAddressAndPrivateKeyToBeConfigured() {
		BesuLedger withoutContract = new BesuLedger(mock(Web3j.class), new BesuProperties(
			new BesuProperties.Rpc("unused"), 1337L, "", privateKey, BigInteger.ZERO, BigInteger.TEN));
		BesuLedger withoutKey = ledger(CONTRACT, "");

		assertThatThrownBy(() -> withoutContract.readHash(1))
			.isInstanceOf(LedgerException.class).hasMessageContaining("besu.contract-address");
		assertThatThrownBy(() -> withoutKey.anchor(1, HASH))
			.isInstanceOf(LedgerException.class).hasMessageContaining("besu.private-key");
	}

}
