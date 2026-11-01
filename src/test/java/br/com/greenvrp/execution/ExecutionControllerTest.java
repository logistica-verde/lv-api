package br.com.greenvrp.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.auth.SecurityConfig;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExecutionController.class)
@Import(SecurityConfig.class)
class ExecutionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ExecutionService executionService;

	private ExecutionResponse response(ExecutionStatus status) {
		return new ExecutionResponse(
			5L, status, Instant.parse("2026-10-20T15:00:00Z"), Instant.parse("2026-10-20T12:00:00Z"), null, null,
			7L, OptimizationCriterion.EMISSION, 1L, "ABC1D23", 3, 5.5, 2022.5, 800.0, null, null);
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void createReturnsCreatedWithLocation() throws Exception {
		given(executionService.create(any())).willReturn(response(ExecutionStatus.SCHEDULED));

		mockMvc.perform(post("/api/executions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"operationId\":7,\"scheduledAt\":\"2026-10-20T15:00:00Z\"}"))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/executions/5"))
			.andExpect(jsonPath("$.status").value("SCHEDULED"))
			.andExpect(jsonPath("$.scheduledAt").value("2026-10-20T15:00:00Z"))
			.andExpect(jsonPath("$.vehiclePlate").value("ABC1D23"));
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void createRequiresTheOperation() throws Exception {
		mockMvc.perform(post("/api/executions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "AUDITOR")
	void anAuditorCannotCreateOrChangeExecutions() throws Exception {
		mockMvc.perform(post("/api/executions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"operationId\":7}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/executions/5/start")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void listsByStatusWithScheduledAsTheDefault() throws Exception {
		given(executionService.list(ExecutionStatus.SCHEDULED)).willReturn(java.util.List.of(response(ExecutionStatus.SCHEDULED)));

		mockMvc.perform(get("/api/executions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(5));
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void transitionsAreConflictsWhenTheStateDoesNotAllowThem() throws Exception {
		given(executionService.start(5L)).willThrow(new ExecutionStateException("Only a scheduled execution can be started"));

		mockMvc.perform(post("/api/executions/5/start"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Only a scheduled execution can be started"));
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void reschedulesAndFinishes() throws Exception {
		given(executionService.reschedule(any(), any())).willReturn(response(ExecutionStatus.SCHEDULED));
		given(executionService.finish(5L)).willReturn(response(ExecutionStatus.FINISHED));

		mockMvc.perform(post("/api/executions/5/reschedule")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"2026-10-21T09:00:00Z\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/executions/5/finish"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("FINISHED"));
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void unknownExecutionIsNotFound() throws Exception {
		given(executionService.get(9L)).willThrow(new ResourceNotFoundException("Execution 9 not found"));

		mockMvc.perform(get("/api/executions/9")).andExpect(status().isNotFound());
	}


	@Test
	@WithMockUser(username = "ana@lastro.local", roles = "OPERATOR")
	void cancelPassesTheReasonAndWhoCancelled() throws Exception {
		given(executionService.cancel(5L, "Veículo com problema mecânico", "ana@lastro.local"))
			.willReturn(response(ExecutionStatus.CANCELLED));

		mockMvc.perform(post("/api/executions/5/cancel")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Veículo com problema mecânico\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	@WithMockUser(roles = "OPERATOR")
	void cancelRequiresAReasonOfAtLeastTenCharacters() throws Exception {
		for (String body : new String[] {"{}", "{\"reason\":\"\"}", "{\"reason\":\"curto\"}", "{\"reason\":\"" + "x".repeat(501) + "\"}"}) {
			mockMvc.perform(post("/api/executions/5/cancel")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isBadRequest());
		}
	}

}
