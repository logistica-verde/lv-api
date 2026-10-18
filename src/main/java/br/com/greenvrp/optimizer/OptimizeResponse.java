package br.com.greenvrp.optimizer;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OptimizeResponse(

	List<Integer> sequence,
	@JsonProperty("total_emission_kg") double totalEmissionKg,
	@JsonProperty("total_distance_meters") double totalDistanceMeters,
	@JsonProperty("segment_emissions") List<SegmentEmission> segmentEmissions,
	@JsonProperty("convergence") @JsonAlias("best_fitness_history") List<Double> convergence

) {

	public OptimizeResponse(
		List<Integer> sequence, double totalEmissionKg, double totalDistanceMeters,
		List<SegmentEmission> segmentEmissions) {
		this(sequence, totalEmissionKg, totalDistanceMeters, segmentEmissions, null);
	}

	public record SegmentEmission(
		@JsonProperty("from_index") int fromIndex,
		@JsonProperty("to_index") int toIndex,
		@JsonProperty("emission_kg") double emissionKg
	) {
	}

}
