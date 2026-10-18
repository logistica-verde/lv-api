package br.com.greenvrp.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

@Entity
@Table(name = "usuarios")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nome;

	@Column(nullable = false, length = 160, unique = true)
	private String email;

	@Column(name = "senha_hash", nullable = false, length = 100)
	private String senhaHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role perfil;

	@Column(nullable = false)
	private boolean ativo = true;

	protected AppUser() {
	}

	public AppUser(String nome, String email, String senhaHash, Role perfil) {
		this.nome = nome;
		this.email = normalize(email);
		this.senhaHash = senhaHash;
		this.perfil = perfil;
	}

	public static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public Role getPerfil() {
		return perfil;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void alterarPerfil(Role novoPerfil) {
		this.perfil = novoPerfil;
	}

	public void ativar() {
		this.ativo = true;
	}

	public void desativar() {
		this.ativo = false;
	}

}
