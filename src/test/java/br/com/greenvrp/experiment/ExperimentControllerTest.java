package br.com.greenvrp.experiment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.auth.SecurityConfig;
import br.com.greenvrp.route.OperationStatus;
import java.time.Instant;
import java.util.List;
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

@WebMvcTest(ExperimentController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "OPERATOR")
class ExperimentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ExperimentService service;

	@Test
	void acceptsAnExperimentAndPointsToTheLatestOne() throws Exception {
		given(service.start(new ExperimentRequest(List.of(10, 15), List.of(1, 2))))
			.willReturn(new ExperimentAcceptedResponse(4L, OperationStatus.PENDING));

		mockMvc.perform(post("/api/experiments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"instances\":[10,15],\"seeds\":[1,2]}"))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Location", "/api/experiments/latest"))
			.andExpect(jsonPath("$.id").value(4))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void acceptsTheLimitsOfTheRanges() throws Exception {
		given(service.start(any())).willReturn(new ExperimentAcceptedResponse(1L, OperationStatus.PENDING));

		mockMvc.perform(post("/api/experiments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"instances\":[2,30],\"seeds\":[1,2,3,4,5,6,7,8,9,10]}"))
			.andExpect(status().isAccepted());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"{}",
		"{\"instances\":[10]}",
		"{\"seeds\":[1]}",
		"{\"instances\":[],\"seeds\":[1]}",
		"{\"instances\":[10],\"seeds\":[]}",
		"{\"instances\":[1],\"seeds\":[1]}",
		"{\"instances\":[31],\"seeds\":[1]}",
		"{\"instances\":[10],\"seeds\":[0]}",
		"{\"instances\":[10],\"seeds\":[-3]}",
		"{\"instances\":[10,null],\"seeds\":[1]}",
		"{\"instances\":[2,3,4,5,6,7,8,9,10,11,12],\"seeds\":[1]}",
		"{\"instances\":[10],\"seeds\":[1,2,3,4,5,6,7,8,9,10,11]}",
		"{\"instances\":\"ten\",\"seeds\":[1]}"
	})
	void rejectsInvalidRequests(String body) throws Exception {
		mockMvc.perform(post("/api/experiments").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest());

		verify(service, never()).start(any());
	}

	@Test
	void answers409WhileAnotherExperimentIsRunning() throws Exception {
		given(service.start(any())).willThrow(new ExperimentAlreadyRunningException());

		mockMvc.perform(post("/api/experiments").contentType(MediaType.APPLICATION_JSON)
				.content("{\"instances\":[10],\"seeds\":[1]}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("An experiment is already running."));
	}

	@Test
	void returnsTheLatestExperimentInTheShapeTheFrontReads() throws Exception {
		ExperimentResponse.CriterionResult emission =
			new ExperimentResponse.CriterionResult(90.0, 12000.0, 1200L, List.of(120.0, 100.0, 90.0));
		ExperimentResponse.CriterionResult distance =
			new ExperimentResponse.CriterionResult(100.0, 10000.0, 1000L, List.of(26.0, 22.0, 20.0));
		given(service.latest()).willReturn(new ExperimentResponse(
			1L, OperationStatus.PENDING, Instant.parse("2026-10-05T12:00:00Z"), List.of(10, 15), List.of(1, 2),
			new ExperimentResponse.Progress(2, 4),
			List.of(new ExperimentResponse.Run(10, 1, emission, distance)), null));

		mockMvc.perform(get("/api/experiments/latest"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(1))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.createdAt").value("2026-10-05T12:00:00Z"))
			.andExpect(jsonPath("$.instances[1]").value(15))
			.andExpect(jsonPath("$.seeds[1]").value(2))
			.andExpect(jsonPath("$.progress.completed").value(2))
			.andExpect(jsonPath("$.progress.total").value(4))
			.andExpect(jsonPath("$.runs[0].instanceSize").value(10))
			.andExpect(jsonPath("$.runs[0].seed").value(1))
			.andExpect(jsonPath("$.runs[0].emission.emissionKg").value(90.0))
			.andExpect(jsonPath("$.runs[0].emission.distanceMeters").value(12000.0))
			.andExpect(jsonPath("$.runs[0].emission.executionMs").value(1200))
			.andExpect(jsonPath("$.runs[0].emission.convergence[2]").value(90.0))
			.andExpect(jsonPath("$.runs[0].distance.emissionKg").value(100.0))
			.andExpect(jsonPath("$.runs[0].distance.convergence[0]").value(26.0))
			.andExpect(jsonPath("$.errorMessage").doesNotExist());
	}

	@Test
	void answers404WhenNoExperimentWasEverRun() throws Exception {
		given(service.latest()).willThrow(new ResourceNotFoundException("No experiment has been run yet"));

		mockMvc.perform(get("/api/experiments/latest")).andExpect(status().isNotFound());
	}

}
