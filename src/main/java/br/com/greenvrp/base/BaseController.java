package br.com.greenvrp.base;

import br.com.greenvrp.api.InvalidRequestException;
import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.deliverypoint.DeliveryPointRepository;
import br.com.greenvrp.geo.CoverageArea;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bases")
public class BaseController {

	private final BaseRepository baseRepository;
	private final DeliveryPointRepository deliveryPointRepository;
	private final CoverageArea coverageArea;

	public BaseController(
		BaseRepository baseRepository, DeliveryPointRepository deliveryPointRepository, CoverageArea coverageArea) {
		this.baseRepository = baseRepository;
		this.deliveryPointRepository = deliveryPointRepository;
		this.coverageArea = coverageArea;
	}

	@PostMapping
	@Transactional
	public ResponseEntity<BaseResponse> create(@Valid @RequestBody BaseRequest request) {
		if (!coverageArea.contains(request.latitude(), request.longitude())) {
			throw new InvalidRequestException("The base is outside the area covered by the road map");
		}
		DeliveryPoint point = new DeliveryPoint(request.nome().trim(), request.endereco(), request.latitude(), request.longitude());
		point.desativar();
		point = deliveryPointRepository.save(point);
		Base base = baseRepository.save(
			new Base(request.nome().trim(), request.endereco(), request.latitude(), request.longitude(), point));
		return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.from(base));
	}

	@GetMapping
	@Transactional(readOnly = true)
	public List<BaseResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
		List<Base> bases = includeInactive
			? baseRepository.findAllByOrderByNomeAsc()
			: baseRepository.findByAtivoTrueOrderByNomeAsc();
		return bases.stream().map(BaseResponse::from).toList();
	}

	@PatchMapping("/{id}")
	@Transactional
	public BaseResponse updateStatus(@PathVariable Long id, @Valid @RequestBody BaseStatusRequest request) {
		Base base = baseRepository.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("Base " + id + " not found"));
		if (request.ativo()) {
			base.ativar();
		}
		else {
			base.desativar();
		}
		return BaseResponse.from(baseRepository.save(base));
	}

}
