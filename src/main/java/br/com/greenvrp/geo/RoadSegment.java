package br.com.greenvrp.geo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.LineString;

@Entity
@Table(name = "malha_viaria")
public class RoadSegment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LineString geom;

	@Column(name = "velocidade_media", nullable = false)
	private Double velocidadeMedia;

	@Column(nullable = false)
	private Double declividade;

	@Column(name = "distancia_metros", nullable = false)
	private Double distanciaMetros;

	protected RoadSegment() {
	}

	public RoadSegment(LineString geom, Double velocidadeMedia, Double declividade, Double distanciaMetros) {
		this.geom = geom;
		this.velocidadeMedia = velocidadeMedia;
		this.declividade = declividade;
		this.distanciaMetros = distanciaMetros;
	}

	public Long getId() {
		return id;
	}

	public LineString getGeom() {
		return geom;
	}

	public Double getVelocidadeMedia() {
		return velocidadeMedia;
	}

	public Double getDeclividade() {
		return declividade;
	}

	public Double getDistanciaMetros() {
		return distanciaMetros;
	}

}
