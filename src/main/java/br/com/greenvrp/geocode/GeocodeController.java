package br.com.greenvrp.geocode;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geocode")
public class GeocodeController {

	private final GeocodeService service;

	public GeocodeController(GeocodeService service) {
		this.service = service;
	}

	@GetMapping
	public List<GeocodeMatch> search(@RequestParam("q") String query) {
		return service.search(query);
	}

}
