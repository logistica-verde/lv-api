package br.com.greenvrp.optimizer;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OptimizerClient {

	private final WebClient optimizerWebClient;
	private final OptimizerProperties properties;

	public OptimizerClient(WebClient optimizerWebClient, OptimizerProperties properties) {
		this.optimizerWebClient = optimizerWebClient;
		this.properties = properties;
	}

	public CompletableFuture<OptimizeResponse> optimize(OptimizeRequest request) {
		return optimizerWebClient.post()
			.uri("/optimize")
			.bodyValue(request)
			.retrieve()
			.bodyToMono(OptimizeResponse.class)
			.timeout(properties.timeout())
			.onErrorMap(error -> new OptimizerException("Optimizer call failed: " + error.getMessage(), error))
			.toFuture();
	}

}
