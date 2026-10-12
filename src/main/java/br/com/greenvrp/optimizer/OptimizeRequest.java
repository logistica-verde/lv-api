package br.com.greenvrp.optimizer;

import br.com.greenvrp.geo.PairSegment;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OptimizeRequest(

	String criterion,
	@JsonProperty("vehicle_mass_kg") double vehicleMassKg,
	@JsonProperty("point_count") int pointCount,
	List<Segment> segments

) {

	public record Segment(
		@JsonProperty("from_index") int fromIndex,
		@JsonProperty("to_index") int toIndex,
		@JsonProperty("distance_meters") double distanceMeters,
		@JsonProperty("average_speed_kmh") double averageSpeedKmh,
		@JsonProperty("slope_percent") double slopePercent
	) {

		static Segment from(PairSegment pair) {
			return new Segment(
				pair.fromIndex(), pair.toIndex(), pair.distanceMeters(),
				pair.averageSpeedKmh(), pair.slopePercent());
		}

	}

	public static OptimizeRequest of(
		OptimizationCriterion criterion, double vehicleMassKg, int pointCount, List<PairSegment> pairs) {
		return new OptimizeRequest(
			criterion.wireValue(),
			vehicleMassKg,
			pointCount,
			pairs.stream().map(Segment::from).toList());
	}

}
