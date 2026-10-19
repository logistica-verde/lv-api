package br.com.greenvrp.execution;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

	List<Execution> findByStatusOrderByAgendadaParaAscIdAsc(ExecutionStatus status);

	List<Execution> findByStatusOrderByIniciadaEmDescIdDesc(ExecutionStatus status);

}
