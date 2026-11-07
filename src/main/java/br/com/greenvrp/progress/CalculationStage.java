package br.com.greenvrp.progress;

public enum CalculationStage {

	QUEUED,
	PREPARING,
	OPTIMIZING,
	SAVING,
	COMPLETED,
	FAILED;

	public boolean isFinal() {
		return this == COMPLETED || this == FAILED;
	}

}
