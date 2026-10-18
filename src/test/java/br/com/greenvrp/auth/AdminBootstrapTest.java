package br.com.greenvrp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {

	private final AppUserRepository users = mock(AppUserRepository.class);
	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private AdminBootstrap bootstrap(String email, String password) {
		return new AdminBootstrap(users, encoder, new BootstrapAdminProperties(email, password, "Administrador"));
	}

	@Test
	void createsTheFirstAdministratorWhenThereAreNoUsers() {
		given(users.count()).willReturn(0L);
		ArgumentCaptor<AppUser> saved = forClass(AppUser.class);

		bootstrap("Admin@Example.test", "senha-inicial").run(null);

		verify(users).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.test");
		assertThat(saved.getValue().getPerfil()).isEqualTo(Role.ADMIN);
		assertThat(saved.getValue().isAtivo()).isTrue();
		assertThat(encoder.matches("senha-inicial", saved.getValue().getSenhaHash())).isTrue();
		assertThat(saved.getValue().getSenhaHash()).doesNotContain("senha-inicial");
	}

	@Test
	void doesNothingWhenThereAreAlreadyUsers() {
		given(users.count()).willReturn(3L);

		bootstrap("admin@example.test", "senha-inicial").run(null);

		verify(users, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void doesNothingWithoutAConfiguredEmailAndPassword() {
		given(users.count()).willReturn(0L);

		bootstrap("", "").run(null);
		bootstrap("admin@example.test", "").run(null);
		bootstrap("", "senha-inicial").run(null);

		verify(users, never()).save(org.mockito.ArgumentMatchers.any());
	}

}
