package br.com.greenvrp.blockchain;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class IntegrityController {

	private final IntegrityVerifier integrityVerifier;

	public IntegrityController(IntegrityVerifier integrityVerifier) {
		this.integrityVerifier = integrityVerifier;
	}

	@GetMapping("/{id}/integrity")
	public IntegrityReport integrity(@PathVariable Long id) {
		return integrityVerifier.verify(id);
	}

}
