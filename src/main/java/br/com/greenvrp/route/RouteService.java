package br.com.greenvrp.route;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService {

	private final VehicleRepository vehicleRepository;
	private final DeliveryPointRepository deliveryPointRepository;
	private final OperationRepository operationRepository;
	private final RouteProcessor routeProcessor;

	public RouteService(
		VehicleRepository vehicleRepository,
		DeliveryPointRepository deliveryPointRepository,
		OperationRepository operationRepository,
		RouteProcessor routeProcessor) {
		this.vehicleRepository = vehicleRepository;
		this.deliveryPointRepository = deliveryPointRepository;
		this.operationRepository = operationRepository;
		this.routeProcessor = routeProcessor;
	}

	public RouteAcceptedResponse calculate(RouteRequest request) {
		List<Long> pointIds = request.deliveryPointIds();
		if (new HashSet<>(pointIds).size() != pointIds.size()) {
			throw new InvalidRequestException("deliveryPointIds must not contain duplicates");
		}
		Vehicle vehicle = activeVehicle(request.vehicleId());
		List<DeliveryPoint> points = pointIds.stream().map(this::activePoint).toList();

		Operation operation = operationRepository.save(Operation.pending(vehicle, request.criterion(), points));
		routeProcessor.processAsync(operation.getId());
		return new RouteAcceptedResponse(operation.getId(), operation.getStatus());
	}

	@Transactional(readOnly = true)
	public RouteResponse get(Long id) {
		return RouteResponse.from(find(id));
	}

	@Transactional(readOnly = true)
	public RouteComparisonResponse compare(Long emissionId, Long distanceId) {
		Operation emission = find(emissionId);
		Operation distance = find(distanceId);
		requireCriterion(emission, OptimizationCriterion.EMISSION, "an emission");
		requireCriterion(distance, OptimizationCriterion.DISTANCE, "a distance");
		if (!emission.getVehicle().getId().equals(distance.getVehicle().getId())) {
			throw new InvalidRequestException("The routes were calculated for different vehicles");
		}
		if (!pointIdsOf(emission).equals(pointIdsOf(distance))) {
			throw new InvalidRequestException("The routes were calculated for different delivery points");
		}
		return new RouteComparisonResponse(RouteResponse.from(emission), RouteResponse.from(distance));
	}

	private Operation find(Long id) {
		return operationRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Route operation %d not found".formatted(id)));
	}

	private void requireCriterion(Operation operation, OptimizationCriterion expected, String description) {
		if (operation.getCriterio() != expected) {
			throw new InvalidRequestException(
				"Operation %d is not %s route".formatted(operation.getId(), description));
		}
	}

	private Set<Long> pointIdsOf(Operation operation) {
		return operation.getPoints().stream()
			.map(item -> item.getPoint().getId())
			.collect(Collectors.toSet());
	}

	private Vehicle activeVehicle(Long id) {
		Vehicle vehicle = vehicleRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Vehicle %d not found".formatted(id)));
		if (!vehicle.isAtivo()) {
			throw new InvalidRequestException("Vehicle %d is inactive".formatted(id));
		}
		return vehicle;
	}

	private DeliveryPoint activePoint(Long id) {
		DeliveryPoint point = deliveryPointRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Delivery point %d not found".formatted(id)));
		if (!point.isAtivo()) {
			throw new InvalidRequestException("Delivery point %d is inactive".formatted(id));
		}
		return point;
	}

}
