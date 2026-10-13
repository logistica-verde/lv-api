package br.com.greenvrp.api;

import br.com.greenvrp.auth.InactiveUserException;
import br.com.greenvrp.auth.InvalidCredentialsException;
import br.com.greenvrp.auth.LastAdministratorException;
import br.com.greenvrp.blockchain.LedgerException;
import br.com.greenvrp.experiment.ExperimentAlreadyRunningException;
import br.com.greenvrp.geocode.GeocoderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(InvalidRequestException.class)
	public ProblemDetail handleInvalidRequest(InvalidRequestException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(LedgerException.class)
	public ProblemDetail handleLedgerUnavailable(LedgerException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
	}

	@ExceptionHandler(InactiveUserException.class)
	public ProblemDetail handleInactiveUser(InactiveUserException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
	}

	@ExceptionHandler(GeocoderUnavailableException.class)
	public ProblemDetail handleGeocoderUnavailable(GeocoderUnavailableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
	}

	@ExceptionHandler(ExperimentAlreadyRunningException.class)
	public ProblemDetail handleExperimentAlreadyRunning(ExperimentAlreadyRunningException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(LastAdministratorException.class)
	public ProblemDetail handleLastAdministrator(LastAdministratorException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

}
