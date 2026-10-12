package br.com.greenvrp.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "lv.cors.allowed-origins=https://app.example.test,http://localhost:5173")
@AutoConfigureMockMvc
class CorsTest {

	private static final String ALLOWED = "https://app.example.test";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void answersThePreflightOfAllowedOriginsWithoutAToken() throws Exception {
		mockMvc.perform(options("/api/vehicles")
				.header(HttpHeaders.ORIGIN, ALLOWED)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
				containsStringIgnoringCase("Authorization")))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"));
	}

	@Test
	void allowsThePatchMethodUsedByTheAdministration() throws Exception {
		mockMvc.perform(options("/api/users/1")
				.header(HttpHeaders.ORIGIN, ALLOWED)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")));
	}

	@Test
	void refusesThePreflightOfOtherOrigins() throws Exception {
		mockMvc.perform(options("/api/vehicles")
				.header(HttpHeaders.ORIGIN, "https://malicious.example.test")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isForbidden())
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void refusesAHeaderThatIsNotOnTheList() throws Exception {
		mockMvc.perform(options("/api/vehicles")
				.header(HttpHeaders.ORIGIN, ALLOWED)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-custom"))
			.andExpect(status().isForbidden());
	}

	@Test
	void marksTheResponsesToAllowedOriginsEvenWhenTheyNeedALogin() throws Exception {
		mockMvc.perform(get("/api/vehicles").header(HttpHeaders.ORIGIN, ALLOWED))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
	}

	@Test
	void doesNotAllowCredentialsBecauseTheTokenTravelsInAHeader() throws Exception {
		mockMvc.perform(options("/api/vehicles")
				.header(HttpHeaders.ORIGIN, ALLOWED)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
	}

	@Test
	void keepsTheDevelopmentOriginOfTheFrontAllowed() throws Exception {
		mockMvc.perform(options("/api/auth/login")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
	}

}
