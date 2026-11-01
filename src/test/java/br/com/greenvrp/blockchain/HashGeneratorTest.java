package br.com.greenvrp.blockchain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.RouteSegment;
import br.com.greenvrp.vehicle.Vehicle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.springframework.test.util.ReflectionTestUtils;

class HashGeneratorTest {

	private static final String GOLDEN_JSON =
		"{\"operationId\":7,\"vehicleId\":1,\"criterion\":\"EMISSION\",\"createdAt\":\"2026-10-05T12:00:00.123Z\","
			+ "\"totalEmissionKg\":5.500000,\"points\":[{\"pointId\":12,\"visitOrder\":1},{\"pointId\":10,\"visitOrder\":2},"
			+ "{\"pointId\":11,\"visitOrder\":3}],\"segments\":[{\"order\":1,\"fromPointId\":12,\"toPointId\":10,"
			+ "\"distanceMeters\":1010.000000,\"averageSpeedKmh\":40.000000,\"slopePercent\":2.000000,\"emissionKg\":2.000000},"
			+ "{\"order\":2,\"fromPointId\":10,\"toPointId\":11,\"distanceMeters\":1012.500000,\"averageSpeedKmh\":40.000000,"
			+ "\"slopePercent\":2.000000,\"emissionKg\":3.500000}]}";

	private static final String GOLDEN_HASH = "abc2628eb60c3a5e5a345b55281d5b209875a155f95aca3855e191cb2879c0c7";

	private final HashGenerator generator = new HashGenerator();

	private DeliveryPoint point(long id, double latitude) {
		DeliveryPoint point = new DeliveryPoint("P" + id, null, latitude, -46.63);
		ReflectionTestUtils.setField(point, "id", id);
		return point;
	}

	private Operation operation(double totalEmission, double baseDistance, List<Integer> sequence, long vehicleId) {
		Vehicle vehicle = new Vehicle("ABC1D23", 7500.0, "diesel");
		ReflectionTestUtils.setField(vehicle, "id", vehicleId);
		List<DeliveryPoint> points = List.of(point(10, -23.55), point(11, -23.56), point(12, -23.57));

		Operation operation = Operation.pending(vehicle, OptimizationCriterion.EMISSION, points);
		ReflectionTestUtils.setField(operation, "id", 7L);
		ReflectionTestUtils.setField(operation, "dataHora", Instant.parse("2026-10-05T12:00:00.123Z"));

		List<RouteSegment> legs = new ArrayList<>();
		for (int position = 0; position < sequence.size() - 1; position++) {
			DeliveryPoint origin = points.get(sequence.get(position));
			DeliveryPoint destination = points.get(sequence.get(position + 1));
			LineString line = new GeometryFactory().createLineString(
				new Coordinate[] {origin.getGeom().getCoordinate(), destination.getGeom().getCoordinate()});
			legs.add(new RouteSegment(
				operation, origin, destination, position + 1, line,
				40.0, 2.0, 2.0 + 1.5 * position, baseDistance + 2.5 * position));
		}
		operation.complete(totalEmission, sequence, legs);
		return operation;
	}

	private Operation baseline() {
		return operation(5.5, 1010.0, List.of(2, 0, 1), 1L);
	}

	@Test
	void producesTheDocumentedCanonicalJson() {
		assertThat(generator.canonicalJson(baseline())).isEqualTo(GOLDEN_JSON);
	}

	@Test
	void includesTheCargoWeightOnlyWhenTheOperationCarriesOne() {
		Operation loaded = baseline();
		ReflectionTestUtils.setField(loaded, "cargaKg", 1200.0);

		assertThat(generator.canonicalJson(loaded))
			.contains("\"createdAt\":\"2026-10-05T12:00:00.123Z\",\"cargoKg\":1200.000000,\"totalEmissionKg\":5.500000");
		assertThat(generator.hash(loaded)).isNotEqualTo(generator.hash(baseline()));
	}

	@Test
	void marksTheReturnToTheBaseOnlyWhenTheOperationReturns() {
		Operation returning = baseline();
		ReflectionTestUtils.setField(returning, "retornaBase", true);

		assertThat(generator.canonicalJson(returning)).contains("\"returnToBase\":true,\"totalEmissionKg\"");
		assertThat(generator.canonicalJson(baseline())).doesNotContain("returnToBase");
		assertThat(generator.hash(returning)).isNotEqualTo(generator.hash(baseline()));
	}

	@Test
	void producesTheKnownSha256ForTheCanonicalJson() {
		assertThat(generator.hash(baseline())).isEqualTo(GOLDEN_HASH);
	}

	@Test
	void sameDataAlwaysProducesTheSameHash() {
		assertThat(generator.hash(baseline())).isEqualTo(generator.hash(baseline()));
	}

	@Test
	void anyChangeToTheOperationDataChangesTheHash() {
		String original = generator.hash(baseline());

		assertThat(generator.hash(operation(6.5, 1010.0, List.of(2, 0, 1), 1L))).isNotEqualTo(original);
		assertThat(generator.hash(operation(5.5, 1011.0, List.of(2, 0, 1), 1L))).isNotEqualTo(original);
		assertThat(generator.hash(operation(5.5, 1010.0, List.of(2, 1, 0), 1L))).isNotEqualTo(original);
		assertThat(generator.hash(operation(5.5, 1010.0, List.of(2, 0, 1), 2L))).isNotEqualTo(original);
	}

	@Test
	void producesTheSameHashWhateverTheTimeZoneOfTheServer() {
		TimeZone original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
			String inSaoPaulo = generator.canonicalJson(baseline());
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
			String inUtc = generator.canonicalJson(baseline());
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
			String inTokyo = generator.canonicalJson(baseline());

			assertThat(inSaoPaulo).isEqualTo(inUtc).isEqualTo(inTokyo);
			assertThat(inUtc).containsPattern("\"createdAt\":\"[0-9T:.\\-]+Z\"");
		}
		finally {
			TimeZone.setDefault(original);
		}
	}

	@Test
	void ignoresTheFieldsThatChangeLegitimatelyAfterTheCalculation() {
		Operation operation = baseline();
		String original = generator.hash(operation);

		operation.registrarTempoExecucao(4_321L);

		assertThat(generator.canonicalJson(operation))
			.doesNotContain("status")
			.doesNotContain("errorMessage")
			.doesNotContain("executionTime")
			.doesNotContain("updatedAt");
		assertThat(generator.hash(operation)).isEqualTo(original);
	}

}
