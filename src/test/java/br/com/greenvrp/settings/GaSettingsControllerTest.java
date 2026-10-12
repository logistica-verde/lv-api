package br.com.greenvrp.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GaSettingsController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class GaSettingsControllerTest {

	private static final String VALID = """
		{"populationSize":150,"generations":300,"crossoverRate":0.9,"mutationRate":0.1}
		""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GaSettingsService service;

	@Test
	void returnsTheCurrentParameters() throws Exception {
		given(service.current()).willReturn(new GaSettingsValues(100, 200, 0.8, 0.05));

		mockMvc.perform(get("/api/settings/ga"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.populationSize").value(100))
			.andExpect(jsonPath("$.generations").value(200))
			.andExpect(jsonPath("$.crossoverRate").value(0.8))
			.andExpect(jsonPath("$.mutationRate").value(0.05));
	}

	@Test
	void savesValidParametersAndReturnsThem() throws Exception {
		GaSettingsValues values = new GaSettingsValues(150, 300, 0.9, 0.1);
		given(service.save(values)).willReturn(values);

		mockMvc.perform(put("/api/settings/ga").contentType(MediaType.APPLICATION_JSON).content(VALID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.populationSize").value(150))
			.andExpect(jsonPath("$.mutationRate").value(0.1));
	}

	@Test
	void acceptsTheLimitsOfEveryRange() throws Exception {
		GaSettingsValues lower = new GaSettingsValues(10, 10, 0.0, 0.0);
		GaSettingsValues upper = new GaSettingsValues(500, 1000, 1.0, 1.0);
		given(service.save(lower)).willReturn(lower);
		given(service.save(upper)).willReturn(upper);

		mockMvc.perform(put("/api/settings/ga").contentType(MediaType.APPLICATION_JSON)
				.content("{\"populationSize\":10,\"generations\":10,\"crossoverRate\":0,\"mutationRate\":0}"))
			.andExpect(status().isOk());
		mockMvc.perform(put("/api/settings/ga").contentType(MediaType.APPLICATION_JSON)
				.content("{\"populationSize\":500,\"generations\":1000,\"crossoverRate\":1,\"mutationRate\":1}"))
			.andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{\"populationSize\":9,\"generations\":300,\"crossoverRate\":0.9,\"mutationRate\":0.1}",
		"{\"populationSize\":501,\"generations\":300,\"crossoverRate\":0.9,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"generations\":9,\"crossoverRate\":0.9,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"generations\":1001,\"crossoverRate\":0.9,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"generations\":300,\"crossoverRate\":-0.1,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"generations\":300,\"crossoverRate\":1.1,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"generations\":300,\"crossoverRate\":0.9,\"mutationRate\":-0.01}",
		"{\"populationSize\":150,\"generations\":300,\"crossoverRate\":0.9,\"mutationRate\":2}"
	})
	void rejectsValuesOutsideTheRange(String body) throws Exception {
		mockMvc.perform(put("/api/settings/ga").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest());
		verify(service, never()).save(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}",
		"{\"generations\":300,\"crossoverRate\":0.9,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"crossoverRate\":0.9,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"generations\":300,\"mutationRate\":0.1}",
		"{\"populationSize\":150,\"generations\":300,\"crossoverRate\":0.9}"
	})
	void rejectsMissingValues(String body) throws Exception {
		mockMvc.perform(put("/api/settings/ga").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest());
		verify(service, never()).save(any());
	}

}
