package br.com.greenvrp.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parametros_ag")
public class GaSettings {

	static final long SINGLE_ROW_ID = 1L;

	@Id
	private Long id = SINGLE_ROW_ID;

	@Column(name = "populacao", nullable = false)
	private int populationSize;

	@Column(name = "geracoes", nullable = false)
	private int generations;

	@Column(name = "taxa_cruzamento", nullable = false)
	private double crossoverRate;

	@Column(name = "taxa_mutacao", nullable = false)
	private double mutationRate;

	protected GaSettings() {
	}

	public GaSettings(GaSettingsValues values) {
		apply(values);
	}

	public void apply(GaSettingsValues values) {
		this.populationSize = values.populationSize();
		this.generations = values.generations();
		this.crossoverRate = values.crossoverRate();
		this.mutationRate = values.mutationRate();
	}

	public GaSettingsValues toValues() {
		return new GaSettingsValues(populationSize, generations, crossoverRate, mutationRate);
	}

}
