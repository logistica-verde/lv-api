package br.com.greenvrp.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

	@Column(nullable = false)
	private boolean ativo = true;

	protected Vehicle() {
	}

	public Vehicle(String placa, Double massaKg, String tipoCombustivel) {
		this.placa = placa;
		this.massaKg = massaKg;
		this.tipoCombustivel = tipoCombustivel;
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

	public boolean isAtivo() {
		return ativo;
	}

	public void desativar() {
		this.ativo = false;
	}

}
