package br.com.greenvrp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.greenvrp.api.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserAdminServiceTest {

	private final AppUserRepository users = mock(AppUserRepository.class);
	private final UserAdminService service = new UserAdminService(users);

	private AppUser user(String email, Role role, boolean active) {
		AppUser user = new AppUser("Pessoa", email, "hash", role);
		ReflectionTestUtils.setField(user, "ativo", active);
		return user;
	}

	private AppUser stored(Long id, AppUser user) {
		given(users.findById(id)).willReturn(Optional.of(user));
		given(users.save(user)).willReturn(user);
		return user;
	}

	@Test
	void listsEveryUserInOrder() {
		List<AppUser> all = List.of(user("a@example.test", Role.ADMIN, true), user("b@example.test", Role.OPERATOR, false));
		given(users.findAllByOrderByIdAsc()).willReturn(all);

		assertThat(service.list()).isEqualTo(all);
	}

	@Test
	void changesTheProfileOnly() {
		AppUser operator = stored(2L, user("carlos@example.test", Role.OPERATOR, true));

		AppUser result = service.update(2L, new UserUpdateRequest(Role.AUDITOR, null));

		assertThat(result.getPerfil()).isEqualTo(Role.AUDITOR);
		assertThat(result.isAtivo()).isTrue();
		assertThat(operator.getEmail()).isEqualTo("carlos@example.test");
	}

	@Test
	void changesTheStatusOnly() {
		stored(2L, user("carlos@example.test", Role.OPERATOR, true));

		AppUser result = service.update(2L, new UserUpdateRequest(null, false));

		assertThat(result.isAtivo()).isFalse();
		assertThat(result.getPerfil()).isEqualTo(Role.OPERATOR);
	}

	@Test
	void reactivatesAnInactiveUser() {
		stored(3L, user("beatriz@example.test", Role.OPERATOR, false));

		assertThat(service.update(3L, new UserUpdateRequest(null, true)).isAtivo()).isTrue();
	}

	@Test
	void changesProfileAndStatusTogether() {
		stored(2L, user("carlos@example.test", Role.OPERATOR, true));

		AppUser result = service.update(2L, new UserUpdateRequest(Role.ADMIN, false));

		assertThat(result.getPerfil()).isEqualTo(Role.ADMIN);
		assertThat(result.isAtivo()).isFalse();
	}

	@Test
	void refusesToDemoteTheLastActiveAdministrator() {
		stored(1L, user("marina@example.test", Role.ADMIN, true));
		given(users.countByPerfilAndAtivoTrue(Role.ADMIN)).willReturn(1L);

		assertThatThrownBy(() -> service.update(1L, new UserUpdateRequest(Role.OPERATOR, null)))
			.isInstanceOf(LastAdministratorException.class);
		verify(users, never()).save(any());
	}

	@Test
	void refusesToDeactivateTheLastActiveAdministrator() {
		stored(1L, user("marina@example.test", Role.ADMIN, true));
		given(users.countByPerfilAndAtivoTrue(Role.ADMIN)).willReturn(1L);

		assertThatThrownBy(() -> service.update(1L, new UserUpdateRequest(null, false)))
			.isInstanceOf(LastAdministratorException.class);
	}

	@Test
	void refusesToDemoteAndDeactivateTheLastAdministratorInOneRequest() {
		stored(1L, user("marina@example.test", Role.ADMIN, true));
		given(users.countByPerfilAndAtivoTrue(Role.ADMIN)).willReturn(1L);

		assertThatThrownBy(() -> service.update(1L, new UserUpdateRequest(Role.AUDITOR, false)))
			.isInstanceOf(LastAdministratorException.class);
	}

	@Test
	void allowsDemotingAnAdministratorWhenAnotherOneRemains() {
		stored(1L, user("marina@example.test", Role.ADMIN, true));
		given(users.countByPerfilAndAtivoTrue(Role.ADMIN)).willReturn(2L);

		assertThat(service.update(1L, new UserUpdateRequest(Role.OPERATOR, null)).getPerfil()).isEqualTo(Role.OPERATOR);
	}

	@Test
	void keepingTheAdministratorAsIsNeverCountsAsLosingIt() {
		stored(1L, user("marina@example.test", Role.ADMIN, true));
		given(users.countByPerfilAndAtivoTrue(Role.ADMIN)).willReturn(1L);

		AppUser result = service.update(1L, new UserUpdateRequest(Role.ADMIN, true));

		assertThat(result.getPerfil()).isEqualTo(Role.ADMIN);
		assertThat(result.isAtivo()).isTrue();
	}

	@Test
	void anInactiveAdministratorDoesNotCountAsTheLastOne() {
		stored(4L, user("antigo@example.test", Role.ADMIN, false));
		given(users.countByPerfilAndAtivoTrue(Role.ADMIN)).willReturn(1L);

		assertThat(service.update(4L, new UserUpdateRequest(Role.OPERATOR, null)).getPerfil()).isEqualTo(Role.OPERATOR);
	}

	@Test
	void failsForAnUnknownUser() {
		given(users.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99L, new UserUpdateRequest(null, false)))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessage("User 99 not found");
	}

}
