package br.com.greenvrp.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.api.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserAdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class UserAdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserAdminService service;

	private AppUser user(Long id, String nome, Role role, boolean active) {
		AppUser user = new AppUser(nome, nome.toLowerCase() + "@example.test", "hash-secreto", role);
		ReflectionTestUtils.setField(user, "id", id);
		ReflectionTestUtils.setField(user, "ativo", active);
		return user;
	}

	@Test
	void listsTheUsersWithoutExposingThePasswordHash() throws Exception {
		given(service.list()).willReturn(List.of(user(1L, "Marina", Role.ADMIN, true), user(2L, "Beatriz", Role.OPERATOR, false)));

		mockMvc.perform(get("/api/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(1))
			.andExpect(jsonPath("$[0].nome").value("Marina"))
			.andExpect(jsonPath("$[0].email").value("marina@example.test"))
			.andExpect(jsonPath("$[0].perfil").value("ADMIN"))
			.andExpect(jsonPath("$[0].ativo").value(true))
			.andExpect(jsonPath("$[1].ativo").value(false))
			.andExpect(content().string(not(containsString("hash-secreto"))))
			.andExpect(content().string(not(containsString("senha"))));
	}

	@Test
	void changesTheProfile() throws Exception {
		given(service.update(2L, new UserUpdateRequest(Role.AUDITOR, null)))
			.willReturn(user(2L, "Carlos", Role.AUDITOR, true));

		mockMvc.perform(patch("/api/users/2").contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"AUDITOR\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.perfil").value("AUDITOR"));
	}

	@Test
	void changesTheStatus() throws Exception {
		given(service.update(2L, new UserUpdateRequest(null, false)))
			.willReturn(user(2L, "Carlos", Role.OPERATOR, false));

		mockMvc.perform(patch("/api/users/2").contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ativo").value(false));
	}

	@Test
	void answers404ForAnUnknownUser() throws Exception {
		given(service.update(99L, new UserUpdateRequest(null, false)))
			.willThrow(new ResourceNotFoundException("User 99 not found"));

		mockMvc.perform(patch("/api/users/99").contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("User 99 not found"));
	}

	@Test
	void answers409WhenItWouldRemoveTheLastAdministrator() throws Exception {
		given(service.update(1L, new UserUpdateRequest(null, false))).willThrow(new LastAdministratorException());

		mockMvc.perform(patch("/api/users/1").contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("The last active administrator cannot be demoted or deactivated."));
	}

	@Test
	void answers400ForAnEmptyUpdate() throws Exception {
		mockMvc.perform(patch("/api/users/2").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		verify(service, never()).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void answers400ForAnUnknownProfile() throws Exception {
		mockMvc.perform(patch("/api/users/2").contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"ROOT\"}"))
			.andExpect(status().isBadRequest());
	}

}
