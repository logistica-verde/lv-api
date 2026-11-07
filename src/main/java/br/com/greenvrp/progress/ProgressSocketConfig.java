package br.com.greenvrp.progress;

import br.com.greenvrp.api.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ProgressSocketConfig implements WebSocketConfigurer {

	public static final String PATH = "/ws/calculations";

	private final CalculationProgressSocket socket;
	private final CorsProperties corsProperties;

	public ProgressSocketConfig(CalculationProgressSocket socket, CorsProperties corsProperties) {
		this.socket = socket;
		this.corsProperties = corsProperties;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(socket, PATH).setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
	}

}
