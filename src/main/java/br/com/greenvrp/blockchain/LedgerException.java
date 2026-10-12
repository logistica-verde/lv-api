package br.com.greenvrp.blockchain;

public class LedgerException extends RuntimeException {

	public LedgerException(String message) {
		super(message);
	}

	public LedgerException(String message, Throwable cause) {
		super(message, cause);
	}

}
