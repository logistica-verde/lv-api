package br.com.greenvrp.experiment;

import br.com.greenvrp.route.OperationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {

	Optional<Experiment> findFirstByOrderByIdDesc();

	boolean existsByStatus(OperationStatus status);

	List<Experiment> findByStatus(OperationStatus status);

}
