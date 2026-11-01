package br.com.greenvrp.execution;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

	List<Execution> findByStatusOrderByAgendadaParaAscIdAsc(ExecutionStatus status);

	List<Execution> findByStatusOrderByIniciadaEmDescIdDesc(ExecutionStatus status);

	Optional<Execution> findByOperationIdAndStatus(Long operationId, ExecutionStatus status);

}
