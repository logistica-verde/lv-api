package br.com.greenvrp.execution;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/executions")
public class ExecutionController {

	private final ExecutionService executionService;

	public ExecutionController(ExecutionService executionService) {
		this.executionService = executionService;
	}

	@PostMapping
	public ResponseEntity<ExecutionResponse> create(@Valid @RequestBody ExecutionRequest request) {
		ExecutionResponse created = executionService.create(request);
		return ResponseEntity.created(URI.create("/api/executions/" + created.id())).body(created);
	}

	@GetMapping
	public List<ExecutionResponse> list(@RequestParam(defaultValue = "SCHEDULED") ExecutionStatus status) {
		return executionService.list(status);
	}

	@GetMapping("/{id}")
	public ExecutionResponse get(@PathVariable Long id) {
		return executionService.get(id);
	}

	@PostMapping("/{id}/start")
	public ExecutionResponse start(@PathVariable Long id) {
		return executionService.start(id);
	}

	@PostMapping("/{id}/reschedule")
	public ExecutionResponse reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
		return executionService.reschedule(id, request.scheduledAt());
	}

	@PostMapping("/{id}/finish")
	public ExecutionResponse finish(@PathVariable Long id) {
		return executionService.finish(id);
	}

	@PostMapping("/{id}/stops/{pointId}/deliver")
	public ExecutionResponse deliverStop(@PathVariable Long id, @PathVariable Long pointId) {
		return executionService.deliverStop(id, pointId);
	}

	@PostMapping("/{id}/stops/{pointId}/cancel")
	public ExecutionResponse cancelStop(
		@PathVariable Long id, @PathVariable Long pointId, @Valid @RequestBody CancelRequest request) {
		return executionService.cancelStop(id, pointId, request.reason());
	}

	@PostMapping("/{id}/replan")
	public ExecutionResponse replan(@PathVariable Long id, @Valid @RequestBody ReplanRequest request) {
		return executionService.replan(id, request.latitude(), request.longitude());
	}

	@PostMapping("/{id}/cancel")
	public ExecutionResponse cancel(
		@PathVariable Long id, @Valid @RequestBody CancelRequest request, Principal principal) {
		return executionService.cancel(id, request.reason(), principal == null ? null : principal.getName());
	}

}
