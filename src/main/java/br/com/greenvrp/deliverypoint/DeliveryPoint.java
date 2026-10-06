package br.com.greenvrp.deliverypoint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

@Entity
@Table(name = "pontos_entrega")
public class DeliveryPoint {

	private static final int WGS84_SRID = 4326;
	private static final GeometryFactory GEOMETRY_FACTORY =
		new GeometryFactory(new PrecisionModel(), WGS84_SRID);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nome;

	@Column(nullable = false)
	private Point geom;

	@Column(length = 255)
	private String endereco;

	@Column(nullable = false)
	private boolean ativo = true;

	protected DeliveryPoint() {
	}

	public DeliveryPoint(String nome, String endereco, double latitude, double longitude) {
		this.nome = nome;
		this.endereco = endereco;
		this.geom = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
		this.ativo = true;
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEndereco() {
		return endereco;
	}

	public Point getGeom() {
		return geom;
	}

	public double getLatitude() {
		return geom.getY();
	}

	public double getLongitude() {
		return geom.getX();
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void desativar() {
		this.ativo = false;
	}

}
