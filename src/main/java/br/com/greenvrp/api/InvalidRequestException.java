package br.com.greenvrp.api;

public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}

}
