package br.com.greenvrp.auth;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	private static final String BODY = """
		{"email":"marina@example.test","senha":"segredo-forte"}
		""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void returnsTheSessionWithoutRequiringAToken() throws Exception {
		given(authService.login(new LoginRequest("marina@example.test", "segredo-forte")))
			.willReturn(new LoginResponse("jwt-token",
				new UserSummary(1L, "Marina Duarte", "marina@example.test", Role.ADMIN)));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").value("jwt-token"))
			.andExpect(jsonPath("$.usuario.id").value(1))
			.andExpect(jsonPath("$.usuario.nome").value("Marina Duarte"))
			.andExpect(jsonPath("$.usuario.perfil").value("ADMIN"));
	}

	@Test
	void answers401ForWrongCredentials() throws Exception {
		given(authService.login(new LoginRequest("marina@example.test", "segredo-forte")))
			.willThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid credentials."));
	}

	@Test
	void answers403ForAnInactiveUser() throws Exception {
		given(authService.login(new LoginRequest("marina@example.test", "segredo-forte")))
			.willThrow(new InactiveUserException());

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("User is inactive."));
	}

	@Test
	void answers400ForAnInvalidBody() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"sem-arroba","senha":""}
					"""))
			.andExpect(status().isBadRequest());
	}

}
