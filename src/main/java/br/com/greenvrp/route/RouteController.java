package br.com.greenvrp.route;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

	private final RouteService routeService;

	public RouteController(RouteService routeService) {
		this.routeService = routeService;
	}

	@PostMapping("/calculate")
	public ResponseEntity<RouteAcceptedResponse> calculate(@Valid @RequestBody RouteRequest request) {
		RouteAcceptedResponse accepted = routeService.calculate(request);
		return ResponseEntity.accepted()
			.location(URI.create("/api/routes/" + accepted.id()))
			.body(accepted);
	}

	@GetMapping("/comparison")
	public RouteComparisonResponse compare(@RequestParam Long emissionId, @RequestParam Long distanceId) {
		return routeService.compare(emissionId, distanceId);
	}

	@GetMapping("/{id}")
	public RouteResponse get(@PathVariable Long id) {
		return routeService.get(id);
	}

}
