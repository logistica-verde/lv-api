package br.com.greenvrp.experiment;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

	private final ExperimentService service;

	public ExperimentController(ExperimentService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ExperimentAcceptedResponse> start(@Valid @RequestBody ExperimentRequest request) {
		ExperimentAcceptedResponse accepted = service.start(request);
		return ResponseEntity.accepted()
			.location(URI.create("/api/experiments/latest"))
			.body(accepted);
	}

	@GetMapping("/latest")
	public ExperimentResponse latest() {
		return service.latest();
	}

}
