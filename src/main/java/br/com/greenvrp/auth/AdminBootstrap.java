package br.com.greenvrp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final BootstrapAdminProperties properties;

	public AdminBootstrap(AppUserRepository users, PasswordEncoder passwordEncoder, BootstrapAdminProperties properties) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		if (!properties.configured() || users.count() > 0) {
			return;
		}
		AppUser admin = new AppUser(
			properties.name(),
			properties.email(),
			passwordEncoder.encode(properties.password()),
			Role.ADMIN);
		users.save(admin);
		log.info("Created the initial administrator {}", admin.getEmail());
	}

}
