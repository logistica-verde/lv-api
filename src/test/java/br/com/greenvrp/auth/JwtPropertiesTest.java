package br.com.greenvrp.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTest {

	@Configuration
	@EnableConfigurationProperties(JwtProperties.class)
	static class PropertiesConfig {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

	@Test
	void acceptsASecretOfAtLeast32Characters() {
		runner.withPropertyValues("security.jwt.secret=0123456789abcdef0123456789abcdef")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(JwtProperties.class).expiration()).isEqualTo(Duration.ofHours(8));
			});
	}

	@Test
	void refusesToStartWithAShortSecret() {
		runner.withPropertyValues("security.jwt.secret=curto").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void refusesToStartWithoutASecret() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void readsTheConfiguredExpiration() {
		runner.withPropertyValues("security.jwt.secret=0123456789abcdef0123456789abcdef", "security.jwt.expiration=30m")
			.run(context -> assertThat(context.getBean(JwtProperties.class).expiration()).isEqualTo(Duration.ofMinutes(30)));
	}

}
