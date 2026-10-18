package br.com.greenvrp.optimizer;

import br.com.greenvrp.geo.PairSegment;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OptimizeRequest(

	String criterion,
	@JsonProperty("vehicle_mass_kg") double vehicleMassKg,
	@JsonProperty("point_count") int pointCount,
	List<Segment> segments,
	@JsonProperty("ga_params") @JsonInclude(JsonInclude.Include.NON_NULL) GaParams gaParams,
	@JsonProperty("seed") @JsonInclude(JsonInclude.Include.NON_NULL) Long seed

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

	public record GaParams(
		@JsonProperty("population_size") int populationSize,
		@JsonProperty("generations") int generations,
		@JsonProperty("crossover_rate") double crossoverRate,
		@JsonProperty("mutation_rate") double mutationRate
	) {
	}

	public static OptimizeRequest of(
		OptimizationCriterion criterion, double vehicleMassKg, int pointCount, List<PairSegment> pairs) {
		return of(criterion, vehicleMassKg, pointCount, pairs, null);
	}

	public static OptimizeRequest of(
		OptimizationCriterion criterion, double vehicleMassKg, int pointCount, List<PairSegment> pairs,
		GaParams gaParams) {
		return of(criterion, vehicleMassKg, pointCount, pairs, gaParams, null);
	}

	public static OptimizeRequest of(
		OptimizationCriterion criterion, double vehicleMassKg, int pointCount, List<PairSegment> pairs,
		GaParams gaParams, Long seed) {
		return new OptimizeRequest(
			criterion.wireValue(),
			vehicleMassKg,
			pointCount,
			pairs.stream().map(Segment::from).toList(),
			gaParams,
			seed);
	}

}
