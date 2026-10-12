package br.com.greenvrp.experiment;

public class ExperimentAlreadyRunningException extends RuntimeException {

	public ExperimentAlreadyRunningException() {
		super("An experiment is already running.");
	}

}
