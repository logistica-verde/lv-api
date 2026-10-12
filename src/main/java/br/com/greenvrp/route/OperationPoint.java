package br.com.greenvrp.route;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "operacao_pontos")
public class OperationPoint {

	@EmbeddedId
	private OperationPointId id = new OperationPointId();

	@MapsId("operationId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_operacao")
	private Operation operation;

	@MapsId("pointId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_ponto")
	private DeliveryPoint point;

	@Column(name = "ordem_visita", nullable = false)
	private int ordemVisita;

	protected OperationPoint() {
	}

	OperationPoint(Operation operation, DeliveryPoint point, int ordemVisita) {
		this.operation = operation;
		this.point = point;
		this.ordemVisita = ordemVisita;
	}

	public DeliveryPoint getPoint() {
		return point;
	}

	public int getOrdemVisita() {
		return ordemVisita;
	}

	void setOrdemVisita(int ordemVisita) {
		this.ordemVisita = ordemVisita;
	}

}
