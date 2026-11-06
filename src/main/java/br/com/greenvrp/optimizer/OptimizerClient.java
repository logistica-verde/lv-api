package br.com.greenvrp.optimizer;

import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class OptimizerClient {

	@FunctionalInterface
	public interface GenerationListener {

		void generation(int generation, int generations);

	}

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

	public CompletableFuture<OptimizeResponse> optimize(OptimizeRequest request, GenerationListener listener) {
		return optimizerWebClient.post()
			.uri("/optimize/stream")
			.accept(MediaType.APPLICATION_NDJSON)
			.bodyValue(request)
			.retrieve()
			.bodyToFlux(OptimizeStreamEvent.class)
			.doOnNext(event -> {
				if (event.isProgress()) {
					listener.generation(event.generation(), event.generations());
				}
			})
			.filter(event -> !event.isProgress())
			.next()
			.flatMap(OptimizerClient::resultOf)
			.switchIfEmpty(Mono.error(new OptimizerException("Optimizer stream ended without a result")))
			.timeout(properties.timeout())
			.onErrorMap(
				error -> !(error instanceof OptimizerException),
				error -> new OptimizerException("Optimizer call failed: " + error.getMessage(), error))
			.toFuture();
	}

	private static Mono<OptimizeResponse> resultOf(OptimizeStreamEvent event) {
		if (event.isResult() && event.result() != null) {
			return Mono.just(event.result());
		}
		return Mono.error(new OptimizerException("Optimizer failed: " + event.detail()));
	}

}
