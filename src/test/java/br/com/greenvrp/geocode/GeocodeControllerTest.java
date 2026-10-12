package br.com.greenvrp.geocode;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.auth.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GeocodeController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "OPERATOR")
class GeocodeControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GeocodeService service;

	@Test
	void returnsTheMatchesInTheFormatOfTheProvider() throws Exception {
		given(service.search("Praça da Sé")).willReturn(List.of(
			new GeocodeMatch("Praça da Sé, São Paulo", "-23.5505", "-46.6333")));

		mockMvc.perform(get("/api/geocode").param("q", "Praça da Sé"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].display_name").value("Praça da Sé, São Paulo"))
			.andExpect(jsonPath("$[0].lat").value("-23.5505"))
			.andExpect(jsonPath("$[0].lon").value("-46.6333"))
			.andExpect(jsonPath("$[0].displayName").doesNotExist());
	}

	@Test
	void returnsAnEmptyListWhenNothingMatches() throws Exception {
		given(service.search("Rua Inexistente")).willReturn(List.of());

		mockMvc.perform(get("/api/geocode").param("q", "Rua Inexistente"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void answers400WhenTheQueryIsMissingOrRefused() throws Exception {
		mockMvc.perform(get("/api/geocode")).andExpect(status().isBadRequest());

		given(service.search("ab")).willThrow(new InvalidRequestException("q must have at least 3 characters"));
		mockMvc.perform(get("/api/geocode").param("q", "ab"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("q must have at least 3 characters"));
	}

	@Test
	void answers503WhenTheProviderIsUnavailable() throws Exception {
		given(service.search("Praça da Sé"))
			.willThrow(new GeocoderUnavailableException("The address search service is unavailable.", new RuntimeException()));

		mockMvc.perform(get("/api/geocode").param("q", "Praça da Sé"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.detail").value("The address search service is unavailable."));
	}

}
