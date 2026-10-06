package br.com.greenvrp.route;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class OperationPointId implements Serializable {

	@Column(name = "id_operacao")
	private Long operationId;

	@Column(name = "id_ponto")
	private Long pointId;

	public OperationPointId() {
	}

	public Long getOperationId() {
		return operationId;
	}

	public Long getPointId() {
		return pointId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof OperationPointId that)) {
			return false;
		}
		return Objects.equals(operationId, that.operationId) && Objects.equals(pointId, that.pointId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(operationId, pointId);
	}

}
