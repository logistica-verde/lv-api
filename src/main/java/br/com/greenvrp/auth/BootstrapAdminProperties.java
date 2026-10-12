package br.com.greenvrp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "security.bootstrap-admin")
public record BootstrapAdminProperties(
	@DefaultValue("") String email,
	@DefaultValue("") String password,
	@DefaultValue("Administrador") String name
) {

	public boolean configured() {
		return !email.isBlank() && !password.isBlank();
	}

}
