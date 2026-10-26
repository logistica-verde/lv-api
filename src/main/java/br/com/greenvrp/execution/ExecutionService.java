package br.com.greenvrp.execution;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.route.Operation;
import br.com.greenvrp.route.OperationRepository;
import br.com.greenvrp.route.OperationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExecutionService {

	static final Duration SCHEDULE_TOLERANCE = Duration.ofMinutes(1);

	private final ExecutionRepository executionRepository;
	private final OperationRepository operationRepository;
	private final Clock clock;

	public ExecutionService(
		ExecutionRepository executionRepository,
		OperationRepository operationRepository,
		@Qualifier("executionClock") Clock clock) {
		this.executionRepository = executionRepository;
		this.operationRepository = operationRepository;
		this.clock = clock;
	}

	@Transactional
	public ExecutionResponse create(ExecutionRequest request) {
		Operation operation = operationRepository.findById(request.operationId())
			.orElseThrow(() -> new ResourceNotFoundException("Operation " + request.operationId() + " not found"));
		if (operation.getStatus() != OperationStatus.COMPLETED) {
			throw new InvalidRequestException("Only a completed route can be executed or scheduled");
		}
		Instant now = clock.instant();
		Execution execution = request.scheduledAt() == null
			? Execution.startedNow(operation, now)
			: Execution.scheduled(operation, requireFuture(request.scheduledAt(), now), now);
		return ExecutionResponse.from(executionRepository.save(execution));
	}

	@Transactional(readOnly = true)
	public List<ExecutionResponse> list(ExecutionStatus status) {
		List<Execution> executions = status == ExecutionStatus.SCHEDULED
			? executionRepository.findByStatusOrderByAgendadaParaAscIdAsc(status)
			: executionRepository.findByStatusOrderByIniciadaEmDescIdDesc(status);
		return executions.stream().map(ExecutionResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public ExecutionResponse get(Long id) {
		return ExecutionResponse.from(find(id));
	}

	@Transactional
	public ExecutionResponse start(Long id) {
		Execution execution = find(id);
		execution.start(clock.instant());
		return ExecutionResponse.from(execution);
	}

	@Transactional
	public ExecutionResponse reschedule(Long id, Instant scheduledAt) {
		Execution execution = find(id);
		execution.reschedule(requireFuture(scheduledAt, clock.instant()));
		return ExecutionResponse.from(execution);
	}

	@Transactional
	public ExecutionResponse finish(Long id) {
		Execution execution = find(id);
		execution.finish(clock.instant());
		return ExecutionResponse.from(execution);
	}

	@Transactional
	public ExecutionResponse cancel(Long id, String reason, String by) {
		Execution execution = find(id);
		execution.cancel(clock.instant(), reason.trim(), by);
		return ExecutionResponse.from(execution);
	}

	private Execution find(Long id) {
		return executionRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Execution " + id + " not found"));
	}

	private Instant requireFuture(Instant scheduledAt, Instant now) {
		if (scheduledAt.isBefore(now.minus(SCHEDULE_TOLERANCE))) {
			throw new InvalidRequestException("The scheduled time must not be in the past");
		}
		return scheduledAt;
	}

}
