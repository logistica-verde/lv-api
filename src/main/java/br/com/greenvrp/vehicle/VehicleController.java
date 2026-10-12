package br.com.greenvrp.vehicle;

import br.com.greenvrp.api.ResourceNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
		Vehicle vehicle = new Vehicle(request.placa(), request.massaKg(), request.tipoCombustivel(), request.imagem());
		Vehicle saved = vehicleRepository.save(vehicle);
		return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponse.from(saved));
	}

	@GetMapping
	public List<VehicleResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
		List<Vehicle> vehicles = includeInactive
			? vehicleRepository.findAllByOrderByIdAsc()
			: vehicleRepository.findByAtivoTrue();
		return vehicles.stream()
			.map(VehicleResponse::from)
			.toList();
	}

	@PatchMapping("/{id}")
	public VehicleResponse updateStatus(@PathVariable Long id, @Valid @RequestBody VehicleStatusRequest request) {
		Vehicle vehicle = vehicleRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Vehicle " + id + " not found"));
		if (request.ativo()) {
			vehicle.ativar();
		}
		else {
			vehicle.desativar();
		}
		return VehicleResponse.from(vehicleRepository.save(vehicle));
	}

}
