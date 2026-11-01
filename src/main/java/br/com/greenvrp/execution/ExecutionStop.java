package br.com.greenvrp.execution;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
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
@Table(name = "execucao_paradas")
public class ExecutionStop {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_execucao")
	private Execution execution;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_ponto")
	private DeliveryPoint point;

	@Column(nullable = false)
	private int ordem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StopStatus status = StopStatus.PENDING;

	@Column(length = 500)
	private String motivo;

	@Column(name = "atualizada_em")
	private Instant atualizadaEm;

	protected ExecutionStop() {
	}

	ExecutionStop(Execution execution, DeliveryPoint point, int ordem) {
		this.execution = execution;
		this.point = point;
		this.ordem = ordem;
	}

	void resolve(StopStatus status, String reason, Instant now) {
		this.status = status;
		this.motivo = reason;
		this.atualizadaEm = now.truncatedTo(ChronoUnit.MILLIS);
	}

	public DeliveryPoint getPoint() {
		return point;
	}

	public int getOrdem() {
		return ordem;
	}

	public StopStatus getStatus() {
		return status;
	}

	public String getMotivo() {
		return motivo;
	}

	public Instant getAtualizadaEm() {
		return atualizadaEm;
	}

}
