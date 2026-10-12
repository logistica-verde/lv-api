package br.com.greenvrp.auth;

public class InactiveUserException extends RuntimeException {

	public InactiveUserException() {
		super("User is inactive.");
	}

}
