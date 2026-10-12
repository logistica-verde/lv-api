package br.com.greenvrp.blockchain;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

final class FakeBesuServer implements AutoCloseable {

	private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
	private static final Pattern FIRST_PARAM = Pattern.compile("\"params\"\\s*:\\s*\\[\\s*\"(0x[0-9a-fA-F]+)\"");

	private final Map<String, Function<String, String>> handlers = new ConcurrentHashMap<>();
	private final HttpServer server;

	FakeBesuServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Matcher method = METHOD.matcher(body);
			Matcher id = ID.matcher(body);
			String methodName = method.find() ? method.group(1) : "";
			String requestId = id.find() ? id.group(1) : "1";
			Function<String, String> handler = handlers.get(methodName);
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
	}

	String url() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	Web3j web3j() {
		return Web3j.build(new HttpService(url()));
	}

	void on(String method, Function<String, String> handler) {
		handlers.put(method, handler);
	}

	void stopAccepting() {
		server.stop(0);
	}

	static String firstParam(String body) {
		Matcher raw = FIRST_PARAM.matcher(body);
		if (!raw.find()) {
			throw new IllegalStateException("No hex parameter in " + body);
		}
		return raw.group(1);
	}

	static String result(String jsonValue) {
		return "{\"jsonrpc\":\"2.0\",\"id\":0,\"result\":" + jsonValue + "}";
	}

	static String error(String message) {
		return "{\"jsonrpc\":\"2.0\",\"id\":0,\"error\":{\"code\":-32000,\"message\":\"" + message + "\"}}";
	}

	static String receipt(String txHash, String status, String contractAddress) {
		String contract = contractAddress == null ? "null" : "\"" + contractAddress + "\"";
		return result("{\"transactionHash\":\"" + txHash + "\",\"transactionIndex\":\"0x0\","
			+ "\"blockHash\":\"0x" + "11".repeat(32) + "\",\"blockNumber\":\"0x2a\","
			+ "\"cumulativeGasUsed\":\"0x5208\",\"gasUsed\":\"0x5208\",\"status\":\"" + status + "\","
			+ "\"contractAddress\":" + contract + ",\"logs\":[],\"logsBloom\":\"0x00\"}");
	}

	@Override
	public void close() {
		server.stop(0);
	}

}
