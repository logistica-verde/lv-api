package br.com.greenvrp.experiment;

import br.com.greenvrp.optimizer.OptimizationCriterion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "experimento_resultados")
public class ExperimentResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_experimento")
	private Experiment experiment;

	@Column(name = "tamanho_instancia", nullable = false)
	private int tamanhoInstancia;

	@Column(nullable = false)
	private int semente;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OptimizationCriterion criterio;

	@Column(name = "emissao_kg", nullable = false)
	private double emissaoKg;

	@Column(name = "distancia_metros", nullable = false)
	private double distanciaMetros;

	@Column(name = "tempo_ms", nullable = false)
	private long tempoMs;

	@Column(nullable = false, length = 32000)
	private String convergencia;

	protected ExperimentResult() {
	}

	public ExperimentResult(
		Experiment experiment, int instanceSize, int seed, OptimizationCriterion criterion,
		double emissionKg, double distanceMeters, long executionMs, List<Double> convergence) {
		this.experiment = experiment;
		this.tamanhoInstancia = instanceSize;
		this.semente = seed;
		this.criterio = criterion;
		this.emissaoKg = emissionKg;
		this.distanciaMetros = distanceMeters;
		this.tempoMs = executionMs;
		this.convergencia = convergence.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	public int getInstanceSize() {
		return tamanhoInstancia;
	}

	public int getSeed() {
		return semente;
	}

	public OptimizationCriterion getCriterion() {
		return criterio;
	}

	public double getEmissionKg() {
		return emissaoKg;
	}

	public double getDistanceMeters() {
		return distanciaMetros;
	}

	public long getExecutionMs() {
		return tempoMs;
	}

	public List<Double> getConvergence() {
		return convergencia.isEmpty()
			? List.of()
			: Arrays.stream(convergencia.split(",")).map(Double::valueOf).toList();
	}

}
