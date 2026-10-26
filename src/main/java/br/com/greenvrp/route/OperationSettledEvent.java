package br.com.greenvrp.route;

public record OperationSettledEvent(Long operationId, boolean completed, String failure) {
}
