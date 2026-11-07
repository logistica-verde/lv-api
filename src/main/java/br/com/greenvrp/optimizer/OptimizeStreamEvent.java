package br.com.greenvrp.optimizer;

public record OptimizeStreamEvent(

	String type,
	Integer generation,
	Integer generations,
	OptimizeResponse result,
	String detail

) {

	public boolean isProgress() {
		return "progress".equals(type) && generation != null && generations != null;
	}

	public boolean isResult() {
		return "result".equals(type);
	}

}
