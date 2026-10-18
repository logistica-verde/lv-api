package br.com.greenvrp.experiment;

import br.com.greenvrp.route.OperationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "experimentos")
public class Experiment {

	private static final int MAX_ERROR_LENGTH = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "data_hora", nullable = false)
	private Instant dataHora;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OperationStatus status;

	@Column(nullable = false, length = 200)
	private String instancias;

	@Column(nullable = false, length = 200)
	private String sementes;

	@Column(name = "total_execucoes", nullable = false)
	private int totalExecucoes;

	@Column(name = "execucoes_concluidas", nullable = false)
	private int execucoesConcluidas;

	@Column(name = "mensagem_erro", length = MAX_ERROR_LENGTH)
	private String mensagemErro;

	@OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("tamanhoInstancia ASC, semente ASC")
	private List<ExperimentResult> results = new ArrayList<>();

	protected Experiment() {
	}

	public static Experiment pending(List<Integer> instances, List<Integer> seeds) {
		Experiment experiment = new Experiment();
		experiment.dataHora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		experiment.status = OperationStatus.PENDING;
		experiment.instancias = join(instances);
		experiment.sementes = join(seeds);
		experiment.totalExecucoes = instances.size() * seeds.size();
		experiment.execucoesConcluidas = 0;
		return experiment;
	}

	private static String join(List<Integer> values) {
		return values.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	private static List<Integer> split(String values) {
		return Arrays.stream(values.split(",")).map(Integer::valueOf).toList();
	}

	public void addRun(ExperimentResult emission, ExperimentResult distance) {
		results.add(emission);
		results.add(distance);
		execucoesConcluidas++;
	}

	public void complete() {
		this.status = OperationStatus.COMPLETED;
		this.mensagemErro = null;
	}

	public void fail(String message) {
		this.status = OperationStatus.FAILED;
		this.mensagemErro = message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
	}

	public Long getId() {
		return id;
	}

	public Instant getDataHora() {
		return dataHora;
	}

	public OperationStatus getStatus() {
		return status;
	}

	public List<Integer> getInstances() {
		return split(instancias);
	}

	public List<Integer> getSeeds() {
		return split(sementes);
	}

	public int getTotalExecucoes() {
		return totalExecucoes;
	}

	public int getExecucoesConcluidas() {
		return execucoesConcluidas;
	}

	public String getMensagemErro() {
		return mensagemErro;
	}

	public List<ExperimentResult> getResults() {
		return Collections.unmodifiableList(results);
	}

}
