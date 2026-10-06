package br.com.greenvrp.blockchain;

import java.time.Instant;

public record IntegrityReport(

	Long operationId,
	IntegrityStatus status,
	String databaseHash,
	String ledgerHash,
	String txHash,
	Long blockNumber,
	Instant anchoredAt

) {
}
