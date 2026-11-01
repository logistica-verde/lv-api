package br.com.greenvrp.blockchain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnchorAttemptRepository extends JpaRepository<AnchorAttempt, Long> {

	List<AnchorAttempt> findByOperationIdOrderByNumberAsc(Long operationId);

	int countByOperationId(Long operationId);

}
