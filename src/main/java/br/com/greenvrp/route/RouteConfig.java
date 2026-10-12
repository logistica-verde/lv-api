package br.com.greenvrp.route;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(HistoryProperties.class)
public class RouteConfig {

	@Bean
	public ThreadPoolTaskExecutor routeExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("route-");
		return executor;
	}

}
