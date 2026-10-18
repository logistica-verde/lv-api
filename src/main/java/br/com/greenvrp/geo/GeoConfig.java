package br.com.greenvrp.geo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({GeoProperties.class, OsrmProperties.class, ElevationProperties.class})
public class GeoConfig {

	@Bean
	public WebClient osrmWebClient(WebClient.Builder builder, OsrmProperties properties) {
		return builder.baseUrl(properties.url()).build();
	}

	@Bean
	public WebClient elevationWebClient(WebClient.Builder builder, ElevationProperties properties) {
		return builder.baseUrl(properties.url()).build();
	}

	@Bean(destroyMethod = "shutdown")
	public ExecutorService geoExecutor(GeoProperties properties) {
		return Executors.newFixedThreadPool(Math.max(1, properties.concurrency()));
	}

}
