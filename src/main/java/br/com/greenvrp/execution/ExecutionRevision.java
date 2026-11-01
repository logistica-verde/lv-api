package br.com.greenvrp.execution;

import br.com.greenvrp.route.Operation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "execucao_revisoes")
public class ExecutionRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_execucao")
	private Execution execution;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_operacao")
	private Operation operation;

	@Column(name = "criada_em", nullable = false)
	private Instant criadaEm;

	protected ExecutionRevision() {
	}

	ExecutionRevision(Execution execution, Operation operation, Instant now) {
		this.execution = execution;
		this.operation = operation;
		this.criadaEm = now.truncatedTo(ChronoUnit.MILLIS);
	}

	public Operation getOperation() {
		return operation;
	}

	public Instant getCriadaEm() {
		return criadaEm;
	}

}
