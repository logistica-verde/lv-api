package br.com.greenvrp.blockchain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockchainRecordRepository extends JpaRepository<BlockchainRecord, Long> {

	Optional<BlockchainRecord> findByOperationId(Long operationId);

	List<BlockchainRecord> findByStatus(AnchorStatus status);

	@Query("select r.operation.id from BlockchainRecord r where r.operation.id in :ids and r.status in :statuses")
	List<Long> findOperationIdsWithStatus(
		@Param("ids") Collection<Long> ids, @Param("statuses") Collection<AnchorStatus> statuses);

}
