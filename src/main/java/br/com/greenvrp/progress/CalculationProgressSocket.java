package br.com.greenvrp.progress;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class CalculationProgressSocket extends TextWebSocketHandler {

	static final CloseStatus INVALID_SUBSCRIPTION = CloseStatus.POLICY_VIOLATION.withReason("Invalid subscription");
	static final int MAX_OPERATIONS = 10;

	private static final Logger log = LoggerFactory.getLogger(CalculationProgressSocket.class);
	private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
	private static final int BUFFER_LIMIT_BYTES = 64 * 1024;

	record Subscription(String token, List<Long> operationIds) {
	}

	private record Subscriber(WebSocketSession session, Set<Long> operationIds, Runnable unsubscribe) {
	}

	private final CalculationProgressTracker tracker;
	private final JwtDecoder jwtDecoder;
	private final ObjectMapper objectMapper;
	private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();

	public CalculationProgressSocket(CalculationProgressTracker tracker, JwtDecoder jwtDecoder, ObjectMapper objectMapper) {
		this.tracker = tracker;
		this.jwtDecoder = jwtDecoder;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
		if (subscribers.containsKey(session.getId())) {
			return;
		}
		Subscription subscription = parse(message.getPayload());
		if (subscription == null || !isValid(subscription.token())) {
			session.close(INVALID_SUBSCRIPTION);
			return;
		}
		WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, BUFFER_LIMIT_BYTES);
		Set<Long> operationIds = Set.copyOf(subscription.operationIds());
		Runnable unsubscribe = tracker.subscribe(progress -> {
			if (operationIds.contains(progress.operationId())) {
				send(safe, progress);
			}
		});
		subscribers.put(session.getId(), new Subscriber(safe, operationIds, unsubscribe));
		operationIds.forEach(operationId -> tracker.latest(operationId).ifPresent(progress -> send(safe, progress)));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		Subscriber subscriber = subscribers.remove(session.getId());
		if (subscriber != null) {
			subscriber.unsubscribe().run();
		}
	}

	private Subscription parse(String payload) {
		try {
			Subscription subscription = objectMapper.readValue(payload, Subscription.class);
			boolean usable = subscription.token() != null && subscription.operationIds() != null
				&& !subscription.operationIds().isEmpty() && subscription.operationIds().size() <= MAX_OPERATIONS
				&& subscription.operationIds().stream().allMatch(id -> id != null && id > 0);
			return usable ? subscription : null;
		}
		catch (JacksonException malformed) {
			return null;
		}
	}

	private boolean isValid(String token) {
		try {
			jwtDecoder.decode(token);
			return true;
		}
		catch (JwtException invalid) {
			return false;
		}
	}

	private void send(WebSocketSession session, CalculationProgress progress) {
		if (!session.isOpen()) {
			return;
		}
		try {
			session.sendMessage(new TextMessage(objectMapper.writeValueAsString(progress)));
		}
		catch (IOException | IllegalStateException failure) {
			log.debug("Could not send the progress of operation {}: {}", progress.operationId(), failure.getMessage());
		}
	}

}
