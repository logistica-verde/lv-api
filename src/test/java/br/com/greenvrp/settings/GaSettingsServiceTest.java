package br.com.greenvrp.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(GaSettingsService.class)
class GaSettingsServiceTest {

	@Autowired
	private GaSettingsService service;

	@Autowired
	private GaSettingsRepository repository;

	@Test
	void usesTheDefaultsWhileNothingWasSaved() {
		assertThat(service.current()).isEqualTo(GaSettingsValues.DEFAULT);
		assertThat(GaSettingsValues.DEFAULT).isEqualTo(new GaSettingsValues(300, 500, 0.9, 0.4));
	}

	@Test
	void savesAndReadsBackTheParameters() {
		GaSettingsValues values = new GaSettingsValues(250, 400, 0.9, 0.1);

		assertThat(service.save(values)).isEqualTo(values);
		assertThat(service.current()).isEqualTo(values);
	}

	@Test
	void keepsASingleRowNoMatterHowManyTimesItIsSaved() {
		service.save(new GaSettingsValues(120, 220, 0.7, 0.02));
		service.save(new GaSettingsValues(130, 230, 0.6, 0.03));
		GaSettingsValues last = service.save(new GaSettingsValues(140, 240, 0.5, 0.04));

		assertThat(repository.count()).isEqualTo(1);
		assertThat(service.current()).isEqualTo(last);
	}

}
