package br.com.greenvrp.progress;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.greenvrp.auth.AppUser;
import br.com.greenvrp.auth.AppUserRepository;
import br.com.greenvrp.auth.Role;
import br.com.greenvrp.auth.TokenService;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalculationProgressSocketTest {

	private static final String ORIGIN = "http://localhost:5173";

	@LocalServerPort
	private int port;

	@Autowired
	private CalculationProgressTracker tracker;

	@Autowired
	private TokenService tokens;

	@Autowired
	private AppUserRepository users;

	private WebSocketSession session;

	private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
	private final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();

	@AfterEach
	void disconnect() throws Exception {
		if (session != null && session.isOpen()) {
			session.close();
		}
	}

	private String token() {
		String email = "operador-" + UUID.randomUUID() + "@example.test";
		return tokens.issue(users.save(new AppUser("Operadora", email, "hash", Role.OPERATOR)));
	}

	private void connect() throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.setOrigin(ORIGIN);
		session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
			@Override
			protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
				received.add(message.getPayload());
			}

			@Override
			public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
				closed.complete(status);
			}
		}, headers, URI.create("ws://localhost:" + port + ProgressSocketConfig.PATH)).get(5, TimeUnit.SECONDS);
	}

	private void subscribe(String token, long... operationIds) throws Exception {
		StringBuilder ids = new StringBuilder();
		for (long id : operationIds) {
			ids.append(ids.isEmpty() ? "" : ",").append(id);
		}
		session.sendMessage(new TextMessage("{\"token\":\"%s\",\"operationIds\":[%s]}".formatted(token, ids)));
	}

	private String next() throws InterruptedException {
		return received.poll(5, TimeUnit.SECONDS);
	}

	@Test
	void sendsTheProgressOfTheSubscribedOperationsAsItHappens() throws Exception {
		connect();
		subscribe(token(), 9001L);
		Thread.sleep(200);

		tracker.publish(CalculationProgress.preparing(9001L, 3, 7));
		tracker.publish(CalculationProgress.optimizing(9002L, 10, 500));
		tracker.publish(CalculationProgress.optimizing(9001L, 250, 500));

		assertThat(next()).contains("\"operationId\":9001").contains("\"stage\":\"PREPARING\"").contains("\"done\":3");
		assertThat(next()).contains("\"operationId\":9001").contains("\"stage\":\"OPTIMIZING\"").contains("\"percent\":60.0");
		assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void sendsTheLatestProgressRightAfterSubscribing() throws Exception {
		tracker.publish(CalculationProgress.completed(9101L));
		connect();

		subscribe(token(), 9101L, 9102L);

		assertThat(next()).contains("\"operationId\":9101").contains("\"stage\":\"COMPLETED\"").contains("\"percent\":100.0");
		assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
	}

	@Test
	void closesTheConnectionWhenTheTokenIsInvalid() throws Exception {
		connect();

		subscribe("not-a-token", 9201L);

		assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
	}

	@Test
	void closesTheConnectionWhenTheSubscriptionIsMalformed() throws Exception {
		connect();

		session.sendMessage(new TextMessage("{\"operationIds\":\"all\"}"));

		assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
	}

}
