package br.com.greenvrp.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.auth.SecurityConfig;
import br.com.greenvrp.blockchain.IntegrityStatus;
import br.com.greenvrp.blockchain.LedgerException;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OperationHistoryController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "OPERATOR")
class OperationHistoryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OperationHistoryService service;

	@Test
	void usesTheNewestFirstAndTenPerPageByDefault() throws Exception {
		given(service.list(any())).willReturn(new OperationPageResponse(List.of(), 0, 1, 10));

		mockMvc.perform(get("/api/routes")).andExpect(status().isOk());

		verify(service).list(new HistoryQuery(null, null, null, null, null, HistorySort.CREATED_AT, false, 1, 10));
	}

	@Test
	void passesEveryFilterAlong() throws Exception {
		given(service.list(any())).willReturn(new OperationPageResponse(List.of(), 0, 2, 25));

		mockMvc.perform(get("/api/routes")
				.param("from", "2026-10-01")
				.param("to", "2026-10-31")
				.param("vehicleId", "3")
				.param("criterion", "DISTANCE")
				.param("integrity", "TAMPERED")
				.param("sort", "totalEmissionKg")
				.param("direction", "asc")
				.param("page", "2")
				.param("size", "25"))
			.andExpect(status().isOk());

		verify(service).list(new HistoryQuery(
			LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 3L, OptimizationCriterion.DISTANCE,
			IntegrityStatus.TAMPERED, HistorySort.TOTAL_EMISSION_KG, true, 2, 25));
	}

	@Test
	void acceptsEverySortField() throws Exception {
		given(service.list(any())).willReturn(new OperationPageResponse(List.of(), 0, 1, 10));

		for (String sort : new String[] {"createdAt", "totalEmissionKg", "totalDistanceMeters"}) {
			mockMvc.perform(get("/api/routes").param("sort", sort)).andExpect(status().isOk());
		}
	}

	@Test
	void returnsThePageInTheShapeTheFrontReads() throws Exception {
		given(service.list(any())).willReturn(new OperationPageResponse(
			List.of(new OperationSummaryResponse(
				7L, Instant.parse("2026-10-05T12:00:00Z"), 1L, "ABC1D23", 12, OptimizationCriterion.EMISSION,
				OperationStatus.COMPLETED, 5.5, 2022.5, IntegrityStatus.VALID)),
			24, 1, 10));

		mockMvc.perform(get("/api/routes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(24))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.items[0].id").value(7))
			.andExpect(jsonPath("$.items[0].createdAt").value("2026-10-05T12:00:00Z"))
			.andExpect(jsonPath("$.items[0].vehicleId").value(1))
			.andExpect(jsonPath("$.items[0].vehiclePlate").value("ABC1D23"))
			.andExpect(jsonPath("$.items[0].pointCount").value(12))
			.andExpect(jsonPath("$.items[0].criterion").value("EMISSION"))
			.andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
			.andExpect(jsonPath("$.items[0].totalEmissionKg").value(5.5))
			.andExpect(jsonPath("$.items[0].totalDistanceMeters").value(2022.5))
			.andExpect(jsonPath("$.items[0].integrityStatus").value("VALID"));
	}

	@ParameterizedTest
	@CsvSource({
		"page,0",
		"page,-1",
		"page,abc",
		"size,0",
		"size,101",
		"size,abc",
		"direction,sideways",
		"sort,price",
		"from,05/10/2026",
		"to,not-a-date",
		"vehicleId,abc",
		"criterion,CHEAPEST",
		"integrity,MAYBE"
	})
	void rejectsInvalidParameters(String name, String value) throws Exception {
		mockMvc.perform(get("/api/routes").param(name, value)).andExpect(status().isBadRequest());

		verify(service, never()).list(any());
	}

	@Test
	void rejectsAnInvertedPeriod() throws Exception {
		mockMvc.perform(get("/api/routes").param("from", "2026-10-31").param("to", "2026-10-01"))
			.andExpect(status().isBadRequest());

		verify(service, never()).list(any());
	}

	@Test
	void mapsAnUnavailableLedgerTo503() throws Exception {
		given(service.list(any())).willThrow(new LedgerException("ledger unreachable"));

		mockMvc.perform(get("/api/routes")).andExpect(status().isServiceUnavailable());
	}

	@Test
	void mapsTooManyOperationsForTheIntegrityFilterTo400() throws Exception {
		given(service.list(any())).willThrow(new InvalidRequestException("Too many operations to filter by integrity"));

		mockMvc.perform(get("/api/routes").param("integrity", "VALID"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Too many operations to filter by integrity"));
	}

}
