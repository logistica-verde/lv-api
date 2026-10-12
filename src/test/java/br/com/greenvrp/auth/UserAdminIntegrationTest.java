package br.com.greenvrp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(UserAdminService.class)
class UserAdminIntegrationTest {

	@Autowired
	private AppUserRepository users;

	@Autowired
	private UserAdminService service;

	private AppUser save(String email, Role role) {
		return users.saveAndFlush(new AppUser("Pessoa", email, "hash", role));
	}

	@Test
	void theOnlyAdministratorCannotBeDemotedOrDeactivated() {
		AppUser only = save("unico@example.test", Role.ADMIN);
		save("operador@example.test", Role.OPERATOR);

		assertThatThrownBy(() -> service.update(only.getId(), new UserUpdateRequest(Role.OPERATOR, null)))
			.isInstanceOf(LastAdministratorException.class);
		assertThatThrownBy(() -> service.update(only.getId(), new UserUpdateRequest(null, false)))
			.isInstanceOf(LastAdministratorException.class);
		assertThat(users.countByPerfilAndAtivoTrue(Role.ADMIN)).isEqualTo(1);
	}

	@Test
	void anAdministratorCanStepDownOnceAnotherOneIsActive() {
		AppUser first = save("primeiro@example.test", Role.ADMIN);
		AppUser second = save("segundo@example.test", Role.OPERATOR);

		service.update(second.getId(), new UserUpdateRequest(Role.ADMIN, null));
		service.update(first.getId(), new UserUpdateRequest(Role.OPERATOR, null));

		assertThat(users.countByPerfilAndAtivoTrue(Role.ADMIN)).isEqualTo(1);
		assertThatThrownBy(() -> service.update(second.getId(), new UserUpdateRequest(null, false)))
			.isInstanceOf(LastAdministratorException.class);
	}

	@Test
	void aDeactivatedAdministratorDoesNotProtectTheLastActiveOne() {
		AppUser active = save("ativo@example.test", Role.ADMIN);
		AppUser inactive = save("inativo@example.test", Role.ADMIN);
		service.update(inactive.getId(), new UserUpdateRequest(null, false));

		assertThatThrownBy(() -> service.update(active.getId(), new UserUpdateRequest(Role.AUDITOR, null)))
			.isInstanceOf(LastAdministratorException.class);
	}

	@Test
	void listsEveryoneInOrderIncludingInactiveUsers() {
		save("a@example.test", Role.ADMIN);
		AppUser b = save("b@example.test", Role.OPERATOR);
		service.update(b.getId(), new UserUpdateRequest(null, false));

		assertThat(service.list()).extracting(AppUser::getEmail).containsExactly("a@example.test", "b@example.test");
	}

}
