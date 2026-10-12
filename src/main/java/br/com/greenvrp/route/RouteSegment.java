package br.com.greenvrp.route;

import br.com.greenvrp.deliverypoint.DeliveryPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.LineString;

@Entity
@Table(name = "segmentos_rota")
public class RouteSegment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_operacao")
	private Operation operation;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_ponto_origem")
	private DeliveryPoint origin;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_ponto_destino")
	private DeliveryPoint destination;

	@Column(nullable = false)
	private int ordem;

	@Column(nullable = false)
	private LineString geom;

	@Column(name = "velocidade_media", nullable = false)
	private double velocidadeMedia;

	@Column(nullable = false)
	private double declividade;

	@Column(name = "emissao_kg_co2", nullable = false)
	private double emissaoKgCo2;

	@Column(name = "distancia_metros", nullable = false)
	private double distanciaMetros;

	protected RouteSegment() {
	}

	public RouteSegment(
		Operation operation, DeliveryPoint origin, DeliveryPoint destination, int ordem,
		LineString geom, double velocidadeMedia, double declividade, double emissaoKgCo2, double distanciaMetros) {
		this.operation = operation;
		this.origin = origin;
		this.destination = destination;
		this.ordem = ordem;
		this.geom = geom;
		this.velocidadeMedia = velocidadeMedia;
		this.declividade = declividade;
		this.emissaoKgCo2 = emissaoKgCo2;
		this.distanciaMetros = distanciaMetros;
	}

	public DeliveryPoint getOrigin() {
		return origin;
	}

	public DeliveryPoint getDestination() {
		return destination;
	}

	public int getOrdem() {
		return ordem;
	}

	public LineString getGeom() {
		return geom;
	}

	public double getVelocidadeMedia() {
		return velocidadeMedia;
	}

	public double getDeclividade() {
		return declividade;
	}

	public double getEmissaoKgCo2() {
		return emissaoKgCo2;
	}

	public double getDistanciaMetros() {
		return distanciaMetros;
	}

}
