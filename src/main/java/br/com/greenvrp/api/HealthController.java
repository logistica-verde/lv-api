package br.com.greenvrp.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/health")
	public Map<String, String> healthCheck() {
		return Map.of("status", "ok");
	}

}
