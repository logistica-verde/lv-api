package br.com.greenvrp.experiment;

import br.com.greenvrp.route.OperationStatus;

public record ExperimentAcceptedResponse(Long id, OperationStatus status) {
}
