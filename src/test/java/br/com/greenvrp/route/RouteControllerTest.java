package br.com.greenvrp.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.auth.SecurityConfig;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RouteController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class RouteControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RouteService routeService;

	private static final String VALID_BODY = """
		{"vehicleId":1,"deliveryPointIds":[10,11,12],"criterion":"EMISSION"}
		""";

	@Test
	void calculateReturnsAcceptedWithLocation() throws Exception {
		given(routeService.calculate(any())).willReturn(new RouteAcceptedResponse(7L, OperationStatus.PENDING));

		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Location", "/api/routes/7"))
			.andExpect(jsonPath("$.id").value(7))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void calculateAcceptsAnOriginInsteadOfASecondPoint() throws Exception {
		given(routeService.calculate(any())).willReturn(new RouteAcceptedResponse(7L, OperationStatus.PENDING));

		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"vehicleId":1,"deliveryPointIds":[10],"criterion":"EMISSION",
					 "origin":{"latitude":-23.5505,"longitude":-46.6333}}
					"""))
			.andExpect(status().isAccepted());
	}

	@Test
	void calculateRejectsAnOriginWithCoordinatesOutOfRangeOrIncomplete() throws Exception {
		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"vehicleId":1,"deliveryPointIds":[10],"criterion":"EMISSION",
					 "origin":{"latitude":91,"longitude":-46.6333}}
					"""))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"vehicleId":1,"deliveryPointIds":[10],"criterion":"EMISSION",
					 "origin":{"latitude":-23.5}}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void calculateRejectsANegativeOrAbsurdCargoWeight() throws Exception {
		for (String cargo : new String[] {"-1", "100001"}) {
			mockMvc.perform(post("/api/routes/calculate")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"vehicleId\":1,\"deliveryPointIds\":[10,11],\"criterion\":\"EMISSION\",\"cargoKg\":" + cargo + "}"))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void calculateRejectsSinglePoint() throws Exception {
		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"vehicleId":1,"deliveryPointIds":[10],"criterion":"EMISSION"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void calculateRejectsMoreThanThirtyPoints() throws Exception {
		String ids = java.util.stream.IntStream.rangeClosed(1, 31)
			.mapToObj(String::valueOf)
			.collect(java.util.stream.Collectors.joining(","));

		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"vehicleId\":1,\"deliveryPointIds\":[" + ids + "],\"criterion\":\"EMISSION\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void calculateRejectsMissingVehicleAndUnknownCriterion() throws Exception {
		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"deliveryPointIds":[10,11],"criterion":"EMISSION"}
					"""))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"vehicleId":1,"deliveryPointIds":[10,11],"criterion":"CHEAPEST"}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void calculateMapsUnknownVehicleToNotFound() throws Exception {
		given(routeService.calculate(any())).willThrow(new ResourceNotFoundException("Vehicle 1 not found"));

		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.detail").value("Vehicle 1 not found"));
	}

	@Test
	void calculateMapsBusinessRuleViolationToBadRequest() throws Exception {
		given(routeService.calculate(any())).willThrow(new InvalidRequestException("Vehicle 1 is inactive"));

		mockMvc.perform(post("/api/routes/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Vehicle 1 is inactive"));
	}

	@Test
	void getReturnsRouteDetails() throws Exception {
		RouteResponse response = new RouteResponse(
			7L, 1L, OptimizationCriterion.EMISSION, OperationStatus.COMPLETED, Instant.parse("2026-10-05T12:00:00Z"),
			5.5, 2010.0, 842L, null,
			List.of(new RouteResponse.PointItem(10L, "P1", -23.55, -46.63, 1)),
			List.of(new RouteResponse.SegmentItem(10L, 11L, 1010.0, 40.0, 2.0, 2.0)));
		given(routeService.get(7L)).willReturn(response);

		mockMvc.perform(get("/api/routes/7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.totalEmissionKg").value(5.5))
			.andExpect(jsonPath("$.executionMs").value(842))
			.andExpect(jsonPath("$.points[0].name").value("P1"))
			.andExpect(jsonPath("$.segments[0].emissionKg").value(2.0));
	}

	private RouteResponse route(long id, OptimizationCriterion criterion, Long executionMs) {
		return new RouteResponse(
			id, 1L, criterion, OperationStatus.COMPLETED, Instant.parse("2026-10-05T12:00:00Z"),
			5.5, 2010.0, executionMs, null,
			List.of(new RouteResponse.PointItem(10L, "P1", -23.55, -46.63, 1)),
			List.of());
	}

	@Test
	void returnsTheStreetGeometryOfEachSegmentAsLatitudeAndLongitude() throws Exception {
		RouteResponse response = new RouteResponse(
			7L, 1L, OptimizationCriterion.EMISSION, OperationStatus.COMPLETED, Instant.parse("2026-10-05T12:00:00Z"),
			5.5, 2010.0, 842L, null,
			List.of(new RouteResponse.PointItem(10L, "P1", -23.55, -46.63, 1)),
			List.of(new RouteResponse.SegmentItem(
				10L, 11L, 1010.0, 40.0, 2.0, 2.0, List.of(List.of(-23.55, -46.63), List.of(-23.56, -46.64)))));
		given(routeService.get(7L)).willReturn(response);

		mockMvc.perform(get("/api/routes/7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.segments[0].geometry[0][0]").value(-23.55))
			.andExpect(jsonPath("$.segments[0].geometry[0][1]").value(-46.63))
			.andExpect(jsonPath("$.segments[0].geometry[1][0]").value(-23.56));
	}

	@Test
	void segmentsWithoutGeometryAnswerAnEmptyList() throws Exception {
		given(routeService.get(7L)).willReturn(new RouteResponse(
			7L, 1L, OptimizationCriterion.EMISSION, OperationStatus.COMPLETED, Instant.parse("2026-10-05T12:00:00Z"),
			5.5, 2010.0, 842L, null, List.of(),
			List.of(new RouteResponse.SegmentItem(10L, 11L, 1010.0, 40.0, 2.0, 2.0))));

		mockMvc.perform(get("/api/routes/7"))
			.andExpect(jsonPath("$.segments[0].geometry").isEmpty());
	}

	@Test
	void pendingRoutesHaveNoExecutionTimeYet() throws Exception {
		RouteResponse pending = new RouteResponse(
			8L, 1L, OptimizationCriterion.EMISSION, OperationStatus.PENDING, Instant.parse("2026-10-05T12:00:00Z"),
			null, null, null, null, List.of(), List.of());
		given(routeService.get(8L)).willReturn(pending);

		mockMvc.perform(get("/api/routes/8"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.executionMs").doesNotExist());
	}

	@Test
	void comparisonReturnsBothRoutesWithTheirExecutionTimes() throws Exception {
		given(routeService.compare(7L, 8L)).willReturn(new RouteComparisonResponse(
			route(7L, OptimizationCriterion.EMISSION, 842L), route(8L, OptimizationCriterion.DISTANCE, 615L)));

		mockMvc.perform(get("/api/routes/comparison").param("emissionId", "7").param("distanceId", "8"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emission.id").value(7))
			.andExpect(jsonPath("$.emission.criterion").value("EMISSION"))
			.andExpect(jsonPath("$.emission.executionMs").value(842))
			.andExpect(jsonPath("$.distance.id").value(8))
			.andExpect(jsonPath("$.distance.criterion").value("DISTANCE"))
			.andExpect(jsonPath("$.distance.executionMs").value(615));
	}

	@Test
	void comparisonOfUnrelatedRoutesIsABadRequest() throws Exception {
		given(routeService.compare(7L, 9L))
			.willThrow(new InvalidRequestException("The routes were calculated for different delivery points"));

		mockMvc.perform(get("/api/routes/comparison").param("emissionId", "7").param("distanceId", "9"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("The routes were calculated for different delivery points"));
	}

	@Test
	void comparisonOfAnUnknownRouteIsNotFound() throws Exception {
		given(routeService.compare(7L, 99L)).willThrow(new ResourceNotFoundException("Route operation 99 not found"));

		mockMvc.perform(get("/api/routes/comparison").param("emissionId", "7").param("distanceId", "99"))
			.andExpect(status().isNotFound());
	}

	@Test
	void comparisonNeedsBothIds() throws Exception {
		mockMvc.perform(get("/api/routes/comparison").param("emissionId", "7"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void getMapsUnknownRouteToNotFound() throws Exception {
		given(routeService.get(99L)).willThrow(new ResourceNotFoundException("Route operation 99 not found"));

		mockMvc.perform(get("/api/routes/99"))
			.andExpect(status().isNotFound());
	}

}
