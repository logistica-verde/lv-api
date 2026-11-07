package br.com.greenvrp.progress;

public record CalculationProgress(

	Long operationId,
	CalculationStage stage,
	double percent,
	Integer done,
	Integer total

) {

	static final double PREPARING_SHARE = 25.0;
	static final double OPTIMIZING_SHARE = 70.0;
	static final double SAVING_PERCENT = PREPARING_SHARE + OPTIMIZING_SHARE;

	public static CalculationProgress queued(Long operationId) {
		return new CalculationProgress(operationId, CalculationStage.QUEUED, 0.0, null, null);
	}

	public static CalculationProgress preparing(Long operationId, int done, int total) {
		return new CalculationProgress(
			operationId, CalculationStage.PREPARING, PREPARING_SHARE * fraction(done, total), done, total);
	}

	public static CalculationProgress optimizing(Long operationId, int generation, int generations) {
		return new CalculationProgress(
			operationId, CalculationStage.OPTIMIZING, PREPARING_SHARE + OPTIMIZING_SHARE * fraction(generation, generations),
			generation, generations);
	}

	public static CalculationProgress saving(Long operationId) {
		return new CalculationProgress(operationId, CalculationStage.SAVING, SAVING_PERCENT, null, null);
	}

	public static CalculationProgress completed(Long operationId) {
		return new CalculationProgress(operationId, CalculationStage.COMPLETED, 100.0, null, null);
	}

	public static CalculationProgress failed(Long operationId) {
		return new CalculationProgress(operationId, CalculationStage.FAILED, 100.0, null, null);
	}

	private static double fraction(int done, int total) {
		return total <= 0 ? 1.0 : Math.min(1.0, Math.max(0.0, (double) done / total));
	}

}
