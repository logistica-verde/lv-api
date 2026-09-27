package br.com.greenvrp.vehicle;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VehicleController.class)
class VehicleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VehicleRepository vehicleRepository;

	@Test
	void createReturnsCreatedVehicle() throws Exception {
		Vehicle saved = new Vehicle("ABC1D23", 7500.0, "diesel");
		given(vehicleRepository.save(org.mockito.ArgumentMatchers.any())).willReturn(saved);

		mockMvc.perform(post("/api/vehicles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"placa":"ABC1D23","massaKg":7500.0,"tipoCombustivel":"diesel"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.placa").value("ABC1D23"))
			.andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	void createWithBlankPlacaReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/vehicles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"placa":"","massaKg":7500.0,"tipoCombustivel":"diesel"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void listReturnsOnlyActiveVehicles() throws Exception {
		given(vehicleRepository.findByAtivoTrue())
			.willReturn(List.of(new Vehicle("ABC1D23", 7500.0, "diesel")));

		mockMvc.perform(get("/api/vehicles"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].placa").value("ABC1D23"));
	}

}
