package br.com.greenvrp.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GaSettingsService {

	private final GaSettingsRepository repository;

	public GaSettingsService(GaSettingsRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public GaSettingsValues current() {
		return repository.findById(GaSettings.SINGLE_ROW_ID)
			.map(GaSettings::toValues)
			.orElse(GaSettingsValues.DEFAULT);
	}

	@Transactional
	public GaSettingsValues save(GaSettingsValues values) {
		GaSettings settings = repository.findById(GaSettings.SINGLE_ROW_ID)
			.orElseGet(() -> new GaSettings(values));
		settings.apply(values);
		return repository.save(settings).toValues();
	}

}
