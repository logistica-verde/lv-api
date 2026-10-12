package br.com.greenvrp.auth;

public record UserSummary(Long id, String nome, String email, Role perfil) {

	public static UserSummary from(AppUser user) {
		return new UserSummary(user.getId(), user.getNome(), user.getEmail(), user.getPerfil());
	}

}
