package br.com.greenvrp.route;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.vehicle.Vehicle;
import br.com.greenvrp.vehicle.VehicleRepository;
import java.util.HashSet;
import java.util.List;
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
		if (new HashSet<>(request.deliveryPointIds()).size() != request.deliveryPointIds().size()) {
			throw new InvalidRequestException("deliveryPointIds must not contain duplicates");
		}
		Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
			.orElseThrow(() -> new ResourceNotFoundException("Vehicle %d not found".formatted(request.vehicleId())));
		if (!vehicle.isAtivo()) {
			throw new InvalidRequestException("Vehicle %d is inactive".formatted(vehicle.getId()));
		}
		List<DeliveryPoint> points = request.deliveryPointIds().stream().map(this::activePoint).toList();

		Operation operation = operationRepository.save(Operation.pending(vehicle, request.criterion(), points));
		routeProcessor.processAsync(operation.getId());
		return new RouteAcceptedResponse(operation.getId(), operation.getStatus());
	}

	@Transactional(readOnly = true)
	public RouteResponse get(Long id) {
		Operation operation = operationRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Route operation %d not found".formatted(id)));
		return RouteResponse.from(operation);
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
