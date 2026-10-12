package br.com.greenvrp.experiment;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.route.OperationStatus;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExperimentService {

	private final ExperimentRepository repository;
	private final ExperimentRunner runner;

	public ExperimentService(ExperimentRepository repository, ExperimentRunner runner) {
		this.repository = repository;
		this.runner = runner;
	}

	public ExperimentAcceptedResponse start(ExperimentRequest request) {
		if (new HashSet<>(request.instances()).size() != request.instances().size()) {
			throw new InvalidRequestException("instances must not contain duplicates");
		}
		if (new HashSet<>(request.seeds()).size() != request.seeds().size()) {
			throw new InvalidRequestException("seeds must not contain duplicates");
		}
		if (repository.existsByStatus(OperationStatus.PENDING)) {
			throw new ExperimentAlreadyRunningException();
		}
		Experiment saved = repository.save(Experiment.pending(request.instances(), request.seeds()));
		runner.runAsync(saved.getId());
		return new ExperimentAcceptedResponse(saved.getId(), saved.getStatus());
	}

	@Transactional(readOnly = true)
	public ExperimentResponse latest() {
		Experiment experiment = repository.findFirstByOrderByIdDesc()
			.orElseThrow(() -> new ResourceNotFoundException("No experiment has been run yet"));
		return ExperimentResponse.from(experiment);
	}

}
