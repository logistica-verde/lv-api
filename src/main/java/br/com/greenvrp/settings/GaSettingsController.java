package br.com.greenvrp.settings;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/ga")
public class GaSettingsController {

	private final GaSettingsService service;

	public GaSettingsController(GaSettingsService service) {
		this.service = service;
	}

	@GetMapping
	public GaSettingsValues get() {
		return service.current();
	}

	@PutMapping
	public GaSettingsValues save(@Valid @RequestBody GaSettingsValues values) {
		return service.save(values);
	}

}
