package br.com.greenvrp.route;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.blockchain.AnchorStatus;
import br.com.greenvrp.blockchain.BlockchainRecordRepository;
import br.com.greenvrp.blockchain.IntegrityStatus;
import br.com.greenvrp.blockchain.IntegrityVerifier;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OperationHistoryService {

	private record Row(
		Long id,
		Instant createdAt,
		Long vehicleId,
		String vehiclePlate,
		int pointCount,
		OptimizationCriterion criterion,
		OperationStatus status,
		Double totalEmissionKg,
		Double totalDistanceMeters
	) {

		static Row from(Operation operation) {
			return new Row(
				operation.getId(),
				operation.getDataHora(),
				operation.getVehicle().getId(),
				operation.getVehicle().getPlaca(),
				operation.getPoints().size(),
				operation.getCriterio(),
				operation.getStatus(),
				operation.getEmissaoTotalKg(),
				operation.getDistanciaTotalMetros());
		}

	}

	private final OperationRepository operationRepository;
	private final BlockchainRecordRepository recordRepository;
	private final IntegrityVerifier integrityVerifier;
	private final HistoryProperties properties;
	private final TransactionTemplate transactions;

	public OperationHistoryService(
		OperationRepository operationRepository,
		BlockchainRecordRepository recordRepository,
		IntegrityVerifier integrityVerifier,
		HistoryProperties properties,
		PlatformTransactionManager transactionManager) {
		this.operationRepository = operationRepository;
		this.recordRepository = recordRepository;
		this.integrityVerifier = integrityVerifier;
		this.properties = properties;
		this.transactions = new TransactionTemplate(transactionManager);
		this.transactions.setReadOnly(true);
	}

	public OperationPageResponse list(HistoryQuery query) {
		Specification<Operation> specification = specification(query);
		Sort sort = Sort.by(query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, query.sort().property())
			.and(Sort.by(Sort.Direction.DESC, "id"));

		if (query.integrity() == null) {
			Page<Row> page = transactions.execute(status -> operationRepository
				.findAll(specification, PageRequest.of(query.page() - 1, query.size(), sort))
				.map(Row::from));
			List<Row> rows = page.getContent();
			List<OperationSummaryResponse> items = summaries(rows, integrityOf(rows));
			return new OperationPageResponse(items, page.getTotalElements(), query.page(), query.size());
		}

		int limit = properties.integrityFilterLimit();
		List<Row> candidates = transactions.execute(status -> operationRepository
			.findAll(specification, PageRequest.of(0, limit + 1, sort))
			.map(Row::from)
			.getContent());
		if (candidates.size() > limit) {
			throw new InvalidRequestException(
				"Too many operations to filter by integrity; narrow the period or the other filters");
		}
		List<IntegrityStatus> statuses = integrityOf(candidates);
		List<OperationSummaryResponse> matching = summaries(candidates, statuses).stream()
			.filter(item -> item.integrityStatus() == query.integrity())
			.toList();
		int from = Math.min((query.page() - 1) * query.size(), matching.size());
		int to = Math.min(from + query.size(), matching.size());
		return new OperationPageResponse(matching.subList(from, to), matching.size(), query.page(), query.size());
	}

	private Specification<Operation> specification(HistoryQuery query) {
		return (root, criteriaQuery, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (query.from() != null) {
				predicates.add(builder.greaterThanOrEqualTo(
					root.get("dataHora"), query.from().atStartOfDay(properties.zone()).toInstant()));
			}
			if (query.to() != null) {
				predicates.add(builder.lessThan(
					root.get("dataHora"), query.to().plusDays(1).atStartOfDay(properties.zone()).toInstant()));
			}
			if (query.vehicleId() != null) {
				predicates.add(builder.equal(root.get("vehicle").get("id"), query.vehicleId()));
			}
			if (query.criterion() != null) {
				predicates.add(builder.equal(root.get("criterio"), query.criterion()));
			}
			return builder.and(predicates.toArray(new Predicate[0]));
		};
	}

	private List<IntegrityStatus> integrityOf(List<Row> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> ids = rows.stream().map(Row::id).toList();
		Set<Long> onLedger = new HashSet<>(
			recordRepository.findOperationIdsWithStatus(ids, List.of(AnchorStatus.ANCHORED, AnchorStatus.DIVERGENT)));
		Set<Long> gaveUp = new HashSet<>(recordRepository.findOperationIdsWithStatus(ids, List.of(AnchorStatus.EXHAUSTED)));
		return rows.stream()
			.map(row -> {
				if (onLedger.contains(row.id())) {
					return integrityVerifier.verify(row.id()).status();
				}
				return gaveUp.contains(row.id()) ? IntegrityStatus.ANCHOR_FAILED : IntegrityStatus.NOT_ANCHORED;
			})
			.toList();
	}

	private List<OperationSummaryResponse> summaries(List<Row> rows, List<IntegrityStatus> statuses) {
		List<OperationSummaryResponse> items = new ArrayList<>();
		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			items.add(new OperationSummaryResponse(
				row.id(), row.createdAt(), row.vehicleId(), row.vehiclePlate(), row.pointCount(), row.criterion(),
				row.status(),
				row.totalEmissionKg(), row.totalDistanceMeters(), statuses.get(index)));
		}
		return items;
	}

}
