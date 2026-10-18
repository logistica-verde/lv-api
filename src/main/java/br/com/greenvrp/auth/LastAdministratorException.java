package br.com.greenvrp.auth;

public class LastAdministratorException extends RuntimeException {

	public LastAdministratorException() {
		super("The last active administrator cannot be demoted or deactivated.");
	}

}
