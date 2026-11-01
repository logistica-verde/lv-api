package br.com.greenvrp.base;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.auth.SecurityConfig;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.geo.CoverageArea;
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

@WebMvcTest(BaseController.class)
@Import(SecurityConfig.class)
class BaseControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BaseRepository baseRepository;

	@MockitoBean
	private DeliveryPointRepository deliveryPointRepository;

	@MockitoBean
	private CoverageArea coverageArea;

	private static final String BODY = """
		{"nome":"Base Central","endereco":"Rua A, 10","latitude":-23.55,"longitude":-46.63}
		""";

	private void stubSaves() {
		given(coverageArea.contains(anyDouble(), anyDouble())).willReturn(true);
		given(deliveryPointRepository.save(any())).willAnswer(call -> call.getArgument(0));
		given(baseRepository.save(any())).willAnswer(call -> call.getArgument(0));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void anAdministratorRegistersABaseAndItsHiddenPoint() throws Exception {
		stubSaves();

		mockMvc.perform(post("/api/bases").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.nome").value("Base Central"))
			.andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void anOperatorCannotRegisterOrChangeABase() throws Exception {
		mockMvc.perform(post("/api/bases").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/bases/1").contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void aBaseOutsideTheCoveredAreaIsRefused() throws Exception {
		given(coverageArea.contains(anyDouble(), anyDouble())).willReturn(false);

		mockMvc.perform(post("/api/bases").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void invalidCoordinatesAndMissingNamesAreRefused() throws Exception {
		for (String body : new String[] {
			"{\"nome\":\"\",\"latitude\":-23.5,\"longitude\":-46.6}",
			"{\"nome\":\"B\",\"latitude\":95,\"longitude\":-46.6}",
			"{\"nome\":\"B\",\"latitude\":-23.5}"}) {
			mockMvc.perform(post("/api/bases").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void anyAuthenticatedUserListsTheActiveBases() throws Exception {
		DeliveryPoint point = new DeliveryPoint("Base Central", null, -23.55, -46.63);
		given(baseRepository.findByAtivoTrueOrderByNomeAsc())
			.willReturn(List.of(new Base("Base Central", null, -23.55, -46.63, point)));

		mockMvc.perform(get("/api/bases"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].nome").value("Base Central"));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void anAdministratorDeactivatesABase() throws Exception {
		DeliveryPoint point = new DeliveryPoint("Base Central", null, -23.55, -46.63);
		Base base = new Base("Base Central", null, -23.55, -46.63, point);
		given(baseRepository.findById(1L)).willReturn(Optional.of(base));
		given(baseRepository.save(any())).willAnswer(call -> call.getArgument(0));

		mockMvc.perform(patch("/api/bases/1").contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ativo").value(false));
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void anUnknownBaseIsNotFound() throws Exception {
		given(baseRepository.findById(9L)).willReturn(Optional.empty());

		mockMvc.perform(patch("/api/bases/9").contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
			.andExpect(status().isNotFound());
	}

}
