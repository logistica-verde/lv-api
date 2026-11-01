package br.com.greenvrp.base;

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

@Entity
@Table(name = "bases")
public class Base {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nome;

	@Column(length = 255)
	private String endereco;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Column(nullable = false)
	private boolean ativo = true;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id_ponto")
	private DeliveryPoint point;

	protected Base() {
	}

	public Base(String nome, String endereco, double latitude, double longitude, DeliveryPoint point) {
		this.nome = nome;
		this.endereco = endereco;
		this.latitude = latitude;
		this.longitude = longitude;
		this.point = point;
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

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public DeliveryPoint getPoint() {
		return point;
	}

	public void ativar() {
		this.ativo = true;
	}

	public void desativar() {
		this.ativo = false;
	}

}
