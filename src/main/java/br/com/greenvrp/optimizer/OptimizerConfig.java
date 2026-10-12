package br.com.greenvrp.optimizer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(OptimizerProperties.class)
public class OptimizerConfig {

	@Bean
	public WebClient optimizerWebClient(WebClient.Builder builder, OptimizerProperties properties) {
		return builder.baseUrl(properties.url()).build();
	}

}
