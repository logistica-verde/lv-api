package br.com.greenvrp.vehicle;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

	private final VehicleRepository vehicleRepository;

	public VehicleController(VehicleRepository vehicleRepository) {
		this.vehicleRepository = vehicleRepository;
	}

	@PostMapping
	public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleRequest request) {
		Vehicle vehicle = new Vehicle(request.placa(), request.massaKg(), request.tipoCombustivel());
		Vehicle saved = vehicleRepository.save(vehicle);
		return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponse.from(saved));
	}

	@GetMapping
	public List<VehicleResponse> list() {
		return vehicleRepository.findByAtivoTrue().stream()
			.map(VehicleResponse::from)
			.toList();
	}

}
