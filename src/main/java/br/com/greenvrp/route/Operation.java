package br.com.greenvrp.route;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import br.com.greenvrp.optimizer.OptimizationCriterion;
import br.com.greenvrp.vehicle.Vehicle;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "operacoes")
public class Operation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_veiculo")
	private Vehicle vehicle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OptimizationCriterion criterio;

	@Column(name = "data_hora", nullable = false)
	private Instant dataHora;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OperationStatus status;

	@Column(name = "emissao_total_kg")
	private Double emissaoTotalKg;

	@Column(name = "mensagem_erro", length = 500)
	private String mensagemErro;

	@OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("ordemVisita ASC")
	private List<OperationPoint> points = new ArrayList<>();

	@OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("ordem ASC")
	private List<RouteSegment> segments = new ArrayList<>();

	protected Operation() {
	}

	public static Operation pending(Vehicle vehicle, OptimizationCriterion criterion, List<DeliveryPoint> points) {
		Operation operation = new Operation();
		operation.vehicle = vehicle;
		operation.criterio = criterion;
		operation.dataHora = Instant.now();
		operation.status = OperationStatus.PENDING;
		for (int index = 0; index < points.size(); index++) {
			operation.points.add(new OperationPoint(operation, points.get(index), index + 1));
		}
		return operation;
	}

	public void complete(double totalEmissionKg, List<Integer> sequence, List<RouteSegment> legs) {
		List<OperationPoint> inputOrder = List.copyOf(points);
		for (int position = 0; position < sequence.size(); position++) {
			inputOrder.get(sequence.get(position)).setOrdemVisita(position + 1);
		}
		this.segments.clear();
		this.segments.addAll(legs);
		this.emissaoTotalKg = totalEmissionKg;
		this.mensagemErro = null;
		this.status = OperationStatus.COMPLETED;
	}

	public void fail(String message) {
		this.status = OperationStatus.FAILED;
		this.mensagemErro = message;
	}

	public Long getId() {
		return id;
	}

	public Vehicle getVehicle() {
		return vehicle;
	}

	public OptimizationCriterion getCriterio() {
		return criterio;
	}

	public Instant getDataHora() {
		return dataHora;
	}

	public OperationStatus getStatus() {
		return status;
	}

	public Double getEmissaoTotalKg() {
		return emissaoTotalKg;
	}

	public String getMensagemErro() {
		return mensagemErro;
	}

	public List<OperationPoint> getPoints() {
		return Collections.unmodifiableList(points);
	}

	public List<RouteSegment> getSegments() {
		return Collections.unmodifiableList(segments);
	}

}
