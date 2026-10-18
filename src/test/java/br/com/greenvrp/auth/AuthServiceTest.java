package br.com.greenvrp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.Mockito;

class AuthServiceTest {

	private final AppUserRepository users = Mockito.mock(AppUserRepository.class);
	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final TokenService tokens = Mockito.mock(TokenService.class);
	private final AuthService service = new AuthService(users, encoder, tokens);

	private AppUser user(String email, Role role) {
		return new AppUser("Marina Duarte", email, encoder.encode("segredo-forte"), role);
	}

	@Test
	void returnsTheTokenAndWhoTheUserIs() {
		AppUser marina = user("marina@example.test", Role.ADMIN);
		given(users.findByEmail("marina@example.test")).willReturn(Optional.of(marina));
		given(tokens.issue(marina)).willReturn("jwt-token");

		LoginResponse response = service.login(new LoginRequest("marina@example.test", "segredo-forte"));

		assertThat(response.token()).isEqualTo("jwt-token");
		assertThat(response.usuario().email()).isEqualTo("marina@example.test");
		assertThat(response.usuario().perfil()).isEqualTo(Role.ADMIN);
	}

	@Test
	void ignoresCaseAndSurroundingSpacesInTheEmail() {
		AppUser marina = user("marina@example.test", Role.OPERATOR);
		given(users.findByEmail("marina@example.test")).willReturn(Optional.of(marina));
		given(tokens.issue(marina)).willReturn("jwt-token");

		LoginResponse response = service.login(new LoginRequest("  Marina@Example.TEST ", "segredo-forte"));

		assertThat(response.token()).isEqualTo("jwt-token");
	}

	@Test
	void rejectsAWrongPassword() {
		given(users.findByEmail("marina@example.test")).willReturn(Optional.of(user("marina@example.test", Role.ADMIN)));

		assertThatThrownBy(() -> service.login(new LoginRequest("marina@example.test", "errada")))
			.isInstanceOf(InvalidCredentialsException.class);
		verify(tokens, never()).issue(any());
	}

	@Test
	void rejectsAnUnknownEmailWithTheSameErrorAsAWrongPassword() {
		given(users.findByEmail("ninguem@example.test")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.login(new LoginRequest("ninguem@example.test", "segredo-forte")))
			.isInstanceOf(InvalidCredentialsException.class)
			.hasMessage("Invalid credentials.");
	}

	@Test
	void rejectsAnInactiveUserOnlyAfterTheCorrectPassword() {
		AppUser inactive = user("beatriz@example.test", Role.OPERATOR);
		org.springframework.test.util.ReflectionTestUtils.setField(inactive, "ativo", false);
		given(users.findByEmail("beatriz@example.test")).willReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.login(new LoginRequest("beatriz@example.test", "segredo-forte")))
			.isInstanceOf(InactiveUserException.class);
		assertThatThrownBy(() -> service.login(new LoginRequest("beatriz@example.test", "errada")))
			.isInstanceOf(InvalidCredentialsException.class);
	}

}
