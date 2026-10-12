package br.com.greenvrp.experiment;

import br.com.greenvrp.route.OperationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExperimentRecovery implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExperimentRecovery.class);

	private final ExperimentRepository repository;

	public ExperimentRecovery(ExperimentRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments arguments) {
		for (Experiment experiment : repository.findByStatus(OperationStatus.PENDING)) {
			experiment.fail("The application restarted before the experiment finished.");
			repository.save(experiment);
			log.warn("Marked the interrupted experiment {} as failed", experiment.getId());
		}
	}

}
