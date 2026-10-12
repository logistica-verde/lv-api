package br.com.greenvrp.route;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.blockchain.IntegrityStatus;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class OperationHistoryController {

	private static final int MAX_PAGE_SIZE = 100;

	private final OperationHistoryService service;

	public OperationHistoryController(OperationHistoryService service) {
		this.service = service;
	}

	@GetMapping
	public OperationPageResponse list(
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
		@RequestParam(required = false) Long vehicleId,
		@RequestParam(required = false) OptimizationCriterion criterion,
		@RequestParam(required = false) IntegrityStatus integrity,
		@RequestParam(defaultValue = "createdAt") String sort,
		@RequestParam(defaultValue = "desc") String direction,
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "10") int size) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new InvalidRequestException("from must not be after to");
		}
		if (page < 1) {
			throw new InvalidRequestException("page must be at least 1");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		if (!direction.equals("asc") && !direction.equals("desc")) {
			throw new InvalidRequestException("direction must be asc or desc");
		}
		HistoryQuery query = new HistoryQuery(
			from, to, vehicleId, criterion, integrity, HistorySort.fromKey(sort), direction.equals("asc"), page, size);
		return service.list(query);
	}

}
