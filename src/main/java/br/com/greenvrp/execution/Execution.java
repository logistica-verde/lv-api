package br.com.greenvrp.execution;

import br.com.greenvrp.route.Operation;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "execucoes")
public class Execution {

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

	public void finish(Instant now) {
		requireStatus(ExecutionStatus.IN_PROGRESS, "Only an execution in progress can be finished");
		this.status = ExecutionStatus.FINISHED;
		this.finalizadaEm = truncated(now);
	}

	public void cancel(Instant now) {
		if (status != ExecutionStatus.SCHEDULED && status != ExecutionStatus.IN_PROGRESS) {
			throw new ExecutionStateException("The execution is already " + status.name().toLowerCase());
		}
		this.status = ExecutionStatus.CANCELLED;
		this.finalizadaEm = truncated(now);
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

}
