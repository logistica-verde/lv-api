package br.com.greenvrp.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

	private static final String PASSWORD = "segredo-forte-123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private TokenService tokens;

	@Autowired
	private JwtEncoder jwtEncoder;

	private AppUser user(Role role) {
		String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
		return users.save(new AppUser("Pessoa " + role, email, encoder.encode(PASSWORD), role));
	}

	private String bearer(Role role) {
		return "Bearer " + tokens.issue(user(role));
	}

	private ResultActions getAs(Role role, String path) throws Exception {
		return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(role)));
	}

	@Test
	void healthCheckIsOpen() throws Exception {
		mockMvc.perform(get("/health")).andExpect(status().isOk());
	}

	@Test
	void everyApiEndpointRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/vehicles"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Authentication is required."));
		mockMvc.perform(get("/api/delivery-points")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/routes/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/routes/1/integrity")).andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsATokenSignedWithAnotherSecret() throws Exception {
		NimbusJwtEncoder foreign = new NimbusJwtEncoder(new ImmutableSecret<>(
			new SecretKeySpec("another-secret-with-32-bytes-long!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("1").issuedAt(now).expiresAt(now.plusSeconds(600)).claim("perfil", "ADMIN").build();
		String forged = foreign.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();

		mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsAnExpiredToken() throws Exception {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("1").issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600))
			.claim("perfil", "ADMIN").build();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();

		mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsATamperedToken() throws Exception {
		String token = tokens.issue(user(Role.OPERATOR));
		String tampered = token.substring(0, token.length() - 3) + "abc";

		mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsASignedTokenThatHasNoValidProfile() throws Exception {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject("1").issuedAt(now).expiresAt(now.plusSeconds(600)).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();

		mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void everyProfileCanReadVehiclesAndDeliveryPoints() throws Exception {
		for (Role role : Role.values()) {
			getAs(role, "/api/vehicles").andExpect(status().isOk());
			getAs(role, "/api/delivery-points").andExpect(status().isOk());
		}
	}

	@Test
	void onlyAdministratorsRegisterVehicles() throws Exception {
		String body = """
			{"placa":"SEC1A11","massaKg":1000.0,"tipoCombustivel":"diesel"}
			""";

		for (Role role : new Role[] {Role.OPERATOR, Role.AUDITOR}) {
			mockMvc.perform(post("/api/vehicles").header(HttpHeaders.AUTHORIZATION, bearer(role))
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Access denied for this profile."));
		}
		mockMvc.perform(post("/api/vehicles").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
	}

	@Test
	void onlyAdministratorsChangeVehicleStatus() throws Exception {
		mockMvc.perform(patch("/api/vehicles/1").header(HttpHeaders.AUTHORIZATION, bearer(Role.OPERATOR))
				.contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void administratorsDeactivateAndReactivateARegisteredVehicle() throws Exception {
		String created = mockMvc.perform(post("/api/vehicles").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"placa\":\"PAT1A11\",\"massaKg\":1000.0,\"tipoCombustivel\":\"diesel\",\"imagem\":\"pickup\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.imagem").value("pickup"))
			.andReturn().getResponse().getContentAsString();
		int id = com.jayway.jsonpath.JsonPath.read(created, "$.id");

		mockMvc.perform(patch("/api/vehicles/" + id).header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
				.contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ativo").value(false));
		getAs(Role.OPERATOR, "/api/vehicles")
			.andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
		getAs(Role.ADMIN, "/api/vehicles?includeInactive=true")
			.andExpect(jsonPath("$[?(@.id == " + id + ")].ativo").value(false));

		mockMvc.perform(patch("/api/vehicles/" + id).header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
				.contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
			.andExpect(jsonPath("$.ativo").value(true));
		getAs(Role.OPERATOR, "/api/vehicles")
			.andExpect(jsonPath("$[?(@.id == " + id + ")]").isNotEmpty());
	}

	@Test
	void auditorsCannotRegisterDeliveryPointsOrCalculateRoutes() throws Exception {
		mockMvc.perform(post("/api/delivery-points").header(HttpHeaders.AUTHORIZATION, bearer(Role.AUDITOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Centro\",\"latitude\":-23.5,\"longitude\":-46.6}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/routes/calculate").header(HttpHeaders.AUTHORIZATION, bearer(Role.AUDITOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehicleId\":1,\"deliveryPointIds\":[1,2],\"criterion\":\"EMISSION\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void operatorsAndAdministratorsReachTheRouteCalculation() throws Exception {
		for (Role role : new Role[] {Role.OPERATOR, Role.ADMIN}) {
			mockMvc.perform(post("/api/routes/calculate").header(HttpHeaders.AUTHORIZATION, bearer(role))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"vehicleId\":999999,\"deliveryPointIds\":[1,2],\"criterion\":\"EMISSION\"}"))
				.andExpect(status().isNotFound());
		}
	}

	@Test
	void routeCalculationRequiresAToken() throws Exception {
		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehicleId\":1,\"deliveryPointIds\":[1,2],\"criterion\":\"EMISSION\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void operatorsCanRegisterDeliveryPoints() throws Exception {
		mockMvc.perform(post("/api/delivery-points").header(HttpHeaders.AUTHORIZATION, bearer(Role.OPERATOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Centro\",\"latitude\":-23.5,\"longitude\":-46.6}"))
			.andExpect(status().isCreated());
	}

	@Test
	void keepsTheIntegrityReportToAuditorsAndAdministrators() throws Exception {
		getAs(Role.OPERATOR, "/api/routes/999/integrity").andExpect(status().isForbidden());
		getAs(Role.AUDITOR, "/api/routes/999/integrity").andExpect(status().isNotFound());
		getAs(Role.ADMIN, "/api/routes/999/integrity").andExpect(status().isNotFound());
	}

	@Test
	void administratorsListAndChangeUsersThroughTheApi() throws Exception {
		AppUser operator = user(Role.OPERATOR);

		getAs(Role.ADMIN, "/api/users")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.email == '" + operator.getEmail() + "')].perfil").value("OPERATOR"))
			.andExpect(content().string(not(containsString("senhaHash"))))
			.andExpect(content().string(not(containsString("$2a$"))));

		mockMvc.perform(patch("/api/users/" + operator.getId()).header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
				.contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"AUDITOR\",\"ativo\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.perfil").value("AUDITOR"))
			.andExpect(jsonPath("$.ativo").value(false));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + operator.getEmail() + "\",\"senha\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void administratorsReadAndSaveTheGeneticAlgorithmParameters() throws Exception {
		getAs(Role.ADMIN, "/api/settings/ga")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.populationSize").isNumber());

		mockMvc.perform(put("/api/settings/ga").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"populationSize\":120,\"generations\":240,\"crossoverRate\":0.7,\"mutationRate\":0.03}"))
			.andExpect(status().isOk());
		getAs(Role.ADMIN, "/api/settings/ga")
			.andExpect(jsonPath("$.populationSize").value(120))
			.andExpect(jsonPath("$.mutationRate").value(0.03));
		mockMvc.perform(put("/api/settings/ga").header(HttpHeaders.AUTHORIZATION, bearer(Role.OPERATOR))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"populationSize\":120,\"generations\":240,\"crossoverRate\":0.7,\"mutationRate\":0.03}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void keepsUserAndSettingsAdministrationToAdministrators() throws Exception {
		for (String path : new String[] {"/api/users", "/api/settings/ga"}) {
			getAs(Role.OPERATOR, path).andExpect(status().isForbidden());
			getAs(Role.AUDITOR, path).andExpect(status().isForbidden());
		}
	}

	@Test
	void deniesAnythingOutsideTheApiThatIsNotPublic() throws Exception {
		getAs(Role.ADMIN, "/actuator/env").andExpect(status().isForbidden());
	}

	@Test
	void allowsTheFullFlowFromLoginToAProtectedCall() throws Exception {
		AppUser admin = user(Role.ADMIN);
		String response = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + admin.getEmail() + "\",\"senha\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.usuario.perfil").value("ADMIN"))
			.andReturn().getResponse().getContentAsString();
		String token = com.jayway.jsonpath.JsonPath.read(response, "$.token");

		mockMvc.perform(get("/api/vehicles").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());
	}

	@Test
	void refusesTheWrongPasswordAndHidesWhetherTheEmailExists() throws Exception {
		AppUser admin = user(Role.ADMIN);

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + admin.getEmail() + "\",\"senha\":\"errada\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid credentials."));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ninguem@example.test\",\"senha\":\"" + PASSWORD + "\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid credentials."));
	}

}
