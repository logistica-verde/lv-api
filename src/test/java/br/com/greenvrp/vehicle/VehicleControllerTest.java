package br.com.greenvrp.vehicle;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.auth.SecurityConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VehicleController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
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
		verify(vehicleRepository, never()).findAllByOrderByIdAsc();
	}

	@Test
	void listIncludesInactiveVehiclesWhenAsked() throws Exception {
		Vehicle inactive = new Vehicle("OLD0A00", 3000.0, "gnv");
		inactive.desativar();
		given(vehicleRepository.findAllByOrderByIdAsc())
			.willReturn(List.of(new Vehicle("ABC1D23", 7500.0, "diesel"), inactive));

		mockMvc.perform(get("/api/vehicles").param("includeInactive", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].ativo").value(true))
			.andExpect(jsonPath("$[1].ativo").value(false));
		verify(vehicleRepository, never()).findByAtivoTrue();
	}

	@Test
	void createKeepsTheChosenImage() throws Exception {
		given(vehicleRepository.save(org.mockito.ArgumentMatchers.any()))
			.willAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/api/vehicles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"placa":"VAN1A11","massaKg":1800.0,"tipoCombustivel":"etanol","imagem":"van"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.imagem").value("van"));
	}

	@Test
	void createWithoutImageUsesTheTruck() throws Exception {
		given(vehicleRepository.save(org.mockito.ArgumentMatchers.any()))
			.willAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/api/vehicles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"placa":"ABC1D23","massaKg":7500.0,"tipoCombustivel":"diesel"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.imagem").value("truck"));
	}

	@Test
	void createWithAnUnknownImageReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/vehicles")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"placa":"ABC1D23","massaKg":7500.0,"tipoCombustivel":"diesel","imagem":"helicopter"}
					"""))
			.andExpect(status().isBadRequest());
		verify(vehicleRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void patchDeactivatesAVehicle() throws Exception {
		Vehicle vehicle = new Vehicle("ABC1D23", 7500.0, "diesel");
		given(vehicleRepository.findById(1L)).willReturn(Optional.of(vehicle));
		given(vehicleRepository.save(vehicle)).willReturn(vehicle);

		mockMvc.perform(patch("/api/vehicles/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ativo\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.placa").value("ABC1D23"))
			.andExpect(jsonPath("$.ativo").value(false));
		verify(vehicleRepository).save(vehicle);
	}

	@Test
	void patchReactivatesAVehicle() throws Exception {
		Vehicle vehicle = new Vehicle("ABC1D23", 7500.0, "diesel");
		vehicle.desativar();
		given(vehicleRepository.findById(1L)).willReturn(Optional.of(vehicle));
		given(vehicleRepository.save(vehicle)).willReturn(vehicle);

		mockMvc.perform(patch("/api/vehicles/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ativo\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	void patchOfAnUnknownVehicleReturnsNotFound() throws Exception {
		given(vehicleRepository.findById(99L)).willReturn(Optional.empty());

		mockMvc.perform(patch("/api/vehicles/99")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ativo\":false}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void patchWithoutTheStatusReturnsBadRequest() throws Exception {
		mockMvc.perform(patch("/api/vehicles/1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
		verify(vehicleRepository, never()).findById(org.mockito.ArgumentMatchers.any());
	}

}
