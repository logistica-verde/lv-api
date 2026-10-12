package br.com.greenvrp.deliverypoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.auth.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeliveryPointController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class DeliveryPointControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DeliveryPointRepository deliveryPointRepository;

	@Test
	void createReturnsCreatedPoint() throws Exception {
		given(deliveryPointRepository.save(any()))
			.willReturn(new DeliveryPoint("Centro", "Praca da Se", -23.5505, -46.6333));

		mockMvc.perform(post("/api/delivery-points")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nome":"Centro","endereco":"Praca da Se","latitude":-23.5505,"longitude":-46.6333}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.nome").value("Centro"))
			.andExpect(jsonPath("$.latitude").value(-23.5505))
			.andExpect(jsonPath("$.longitude").value(-46.6333));
	}

	@Test
	void createWithOutOfRangeLatitudeReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/delivery-points")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"nome":"Invalido","latitude":123.0,"longitude":-46.6333}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listReturnsActivePoints() throws Exception {
		given(deliveryPointRepository.findByAtivoTrue())
			.willReturn(List.of(new DeliveryPoint("Centro", null, -23.5505, -46.6333)));

		mockMvc.perform(get("/api/delivery-points"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].nome").value("Centro"));
	}

}
