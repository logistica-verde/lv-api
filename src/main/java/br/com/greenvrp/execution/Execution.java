package br.com.greenvrp.execution;

import br.com.greenvrp.api.ResourceNotFoundException;
import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.route.Operation;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "execucoes")
public class Execution {

	private static final int MAX_MESSAGE = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_operacao")
	private Operation operation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ExecutionStatus status;

	@Column(name = "agendada_para")
	private Instant agendadaPara;

	@Column(name = "criada_em", nullable = false)
	private Instant criadaEm;

	@Column(name = "iniciada_em")
	private Instant iniciadaEm;

	@Column(name = "finalizada_em")
	private Instant finalizadaEm;

	@Column(name = "motivo_cancelamento", length = 500)
	private String motivoCancelamento;

	@Column(name = "cancelada_por", length = 150)
	private String canceladaPor;

	@Column(nullable = false)
	private int revisao;

	@Column(name = "mensagem_replanejamento", length = 500)
	private String mensagemReplanejamento;

	@OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("ordem ASC")
	private List<ExecutionStop> stops = new ArrayList<>();

	@OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<ExecutionRevision> revisions = new ArrayList<>();

	protected Execution() {
	}

	public static Execution startedNow(Operation operation, Instant now) {
		Execution execution = new Execution();
		execution.operation = operation;
		execution.criadaEm = truncated(now);
		execution.status = ExecutionStatus.IN_PROGRESS;
		execution.iniciadaEm = execution.criadaEm;
		return execution;
	}

	public static Execution scheduled(Operation operation, Instant scheduledFor, Instant now) {
		Execution execution = new Execution();
		execution.operation = operation;
		execution.criadaEm = truncated(now);
		execution.status = ExecutionStatus.SCHEDULED;
		execution.agendadaPara = truncated(scheduledFor);
		return execution;
	}

	public void start(Instant now) {
		requireStatus(ExecutionStatus.SCHEDULED, "Only a scheduled execution can be started");
		this.status = ExecutionStatus.IN_PROGRESS;
		this.iniciadaEm = truncated(now);
	}

	public void reschedule(Instant scheduledFor) {
		requireStatus(ExecutionStatus.SCHEDULED, "Only a scheduled execution can be rescheduled");
		this.agendadaPara = truncated(scheduledFor);
	}

	public void addStops(List<DeliveryPoint> points) {
		for (int index = 0; index < points.size(); index++) {
			stops.add(new ExecutionStop(this, points.get(index), index + 1));
		}
	}

	public void resolveStop(Long pointId, StopStatus status, String reason, Instant now) {
		requireStatus(ExecutionStatus.IN_PROGRESS, "Stops can only be updated while the execution is in progress");
		ExecutionStop stop = stops.stream()
			.filter(candidate -> candidate.getPoint().getId().equals(pointId))
			.findFirst()
			.orElseThrow(() -> new ResourceNotFoundException("Stop " + pointId + " is not part of this execution"));
		if (stop.getStatus() != StopStatus.PENDING) {
			throw new ExecutionStateException("The stop is already " + stop.getStatus().name().toLowerCase());
		}
		stop.resolve(status, reason, now);
	}

	public List<Long> pendingPointIds() {
		return stops.stream()
			.filter(stop -> stop.getStatus() == StopStatus.PENDING)
			.map(stop -> stop.getPoint().getId())
			.toList();
	}

	public void startReplan(Operation replacement, Instant now) {
		requireStatus(ExecutionStatus.IN_PROGRESS, "Only an execution in progress can be replanned");
		revisions.add(new ExecutionRevision(this, operation, now));
		this.operation = replacement;
		this.revisao++;
		this.status = ExecutionStatus.REPLANNING;
		this.mensagemReplanejamento = null;
	}

	public void settleReplan(String failure) {
		if (status != ExecutionStatus.REPLANNING) {
			return;
		}
		if (failure != null && !revisions.isEmpty()) {
			this.operation = revisions.get(revisions.size() - 1).getOperation();
			this.mensagemReplanejamento = failure.length() > MAX_MESSAGE ? failure.substring(0, MAX_MESSAGE) : failure;
		}
		this.status = ExecutionStatus.IN_PROGRESS;
	}

	public void finish(Instant now) {
		requireStatus(ExecutionStatus.IN_PROGRESS, "Only an execution in progress can be finished");
		this.status = ExecutionStatus.FINISHED;
		this.finalizadaEm = truncated(now);
	}

	public void cancel(Instant now, String reason, String by) {
		if (status != ExecutionStatus.SCHEDULED && status != ExecutionStatus.IN_PROGRESS
			&& status != ExecutionStatus.REPLANNING) {
			throw new ExecutionStateException("The execution is already " + status.name().toLowerCase());
		}
		this.status = ExecutionStatus.CANCELLED;
		this.finalizadaEm = truncated(now);
		this.motivoCancelamento = reason;
		this.canceladaPor = by;
	}

	private void requireStatus(ExecutionStatus expected, String message) {
		if (status != expected) {
			throw new ExecutionStateException(message);
		}
	}

	private static Instant truncated(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MILLIS);
	}

	public Long getId() {
		return id;
	}

	public Operation getOperation() {
		return operation;
	}

	public ExecutionStatus getStatus() {
		return status;
	}

	public Instant getAgendadaPara() {
		return agendadaPara;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

	public Instant getIniciadaEm() {
		return iniciadaEm;
	}

	public Instant getFinalizadaEm() {
		return finalizadaEm;
	}

	public int getRevisao() {
		return revisao;
	}

	public String getMensagemReplanejamento() {
		return mensagemReplanejamento;
	}

	public List<ExecutionStop> getStops() {
		return Collections.unmodifiableList(stops);
	}

	public String getMotivoCancelamento() {
		return motivoCancelamento;
	}

	public String getCanceladaPor() {
		return canceladaPor;
	}

}
