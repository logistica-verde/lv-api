package br.com.greenvrp.execution;

import br.com.greenvrp.route.OperationSettledEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExecutionReplanListener {

	private final ExecutionRepository executionRepository;

	public ExecutionReplanListener(ExecutionRepository executionRepository) {
		this.executionRepository = executionRepository;
	}

	@EventListener
	@Transactional
	public void onOperationSettled(OperationSettledEvent event) {
		executionRepository.findByOperationIdAndStatus(event.operationId(), ExecutionStatus.REPLANNING)
			.ifPresent(execution -> execution.settleReplan(event.completed() ? null : failureOf(event)));
	}

	private static String failureOf(OperationSettledEvent event) {
		return event.failure() == null ? "The new route could not be calculated" : event.failure();
	}

}
