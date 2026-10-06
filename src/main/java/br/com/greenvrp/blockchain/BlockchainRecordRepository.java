package br.com.greenvrp.blockchain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainRecordRepository extends JpaRepository<BlockchainRecord, Long> {

	Optional<BlockchainRecord> findByOperationId(Long operationId);

}
