package br.com.greenvrp.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "veiculos")
public class Vehicle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 10)
	private String placa;

	@Column(name = "massa_kg", nullable = false)
	private Double massaKg;

	@Column(name = "tipo_combustivel", nullable = false, length = 30)
	private String tipoCombustivel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private VehicleImage imagem = VehicleImage.DEFAULT;

	@Column(nullable = false)
	private boolean ativo = true;

	protected Vehicle() {
	}

	public Vehicle(String placa, Double massaKg, String tipoCombustivel) {
		this(placa, massaKg, tipoCombustivel, VehicleImage.DEFAULT);
	}

	public Vehicle(String placa, Double massaKg, String tipoCombustivel, VehicleImage imagem) {
		this.placa = placa;
		this.massaKg = massaKg;
		this.tipoCombustivel = tipoCombustivel;
		this.imagem = imagem != null ? imagem : VehicleImage.DEFAULT;
		this.ativo = true;
	}

	public Long getId() {
		return id;
	}

	public String getPlaca() {
		return placa;
	}

	public Double getMassaKg() {
		return massaKg;
	}

	public String getTipoCombustivel() {
		return tipoCombustivel;
	}

	public VehicleImage getImagem() {
		return imagem;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void desativar() {
		this.ativo = false;
	}

	public void ativar() {
		this.ativo = true;
	}

}
