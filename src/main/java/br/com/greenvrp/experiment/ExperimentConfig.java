package br.com.greenvrp.experiment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ExperimentProperties.class)
public class ExperimentConfig {

	@Bean
	public ThreadPoolTaskExecutor experimentExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(5);
		executor.setThreadNamePrefix("experiment-");
		return executor;
	}

}
