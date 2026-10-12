package br.com.greenvrp.deliverypoint;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery-points")
public class DeliveryPointController {

	private final DeliveryPointRepository deliveryPointRepository;

	public DeliveryPointController(DeliveryPointRepository deliveryPointRepository) {
		this.deliveryPointRepository = deliveryPointRepository;
	}

	@PostMapping
	public ResponseEntity<DeliveryPointResponse> create(@Valid @RequestBody DeliveryPointRequest request) {
		DeliveryPoint point = new DeliveryPoint(
			request.nome(), request.endereco(), request.latitude(), request.longitude());
		DeliveryPoint saved = deliveryPointRepository.save(point);
		return ResponseEntity.status(HttpStatus.CREATED).body(DeliveryPointResponse.from(saved));
	}

	@GetMapping
	public List<DeliveryPointResponse> list() {
		return deliveryPointRepository.findByAtivoTrue().stream()
			.map(DeliveryPointResponse::from)
			.toList();
	}

}
