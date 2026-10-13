package br.com.greenvrp.geocode;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(GeocodeProperties.class)
public class GeocodeConfig {

	@Bean
	public WebClient geocoderWebClient(WebClient.Builder builder) {
		return builder.build();
	}

	@Bean
	public Clock geocodeClock() {
		return Clock.systemUTC();
	}

}
