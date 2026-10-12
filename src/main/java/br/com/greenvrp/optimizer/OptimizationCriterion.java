package br.com.greenvrp.optimizer;

public enum OptimizationCriterion {

	EMISSION("emission"),
	DISTANCE("distance");

	private final String wireValue;

	OptimizationCriterion(String wireValue) {
		this.wireValue = wireValue;
	}

	public String wireValue() {
		return wireValue;
	}

}
