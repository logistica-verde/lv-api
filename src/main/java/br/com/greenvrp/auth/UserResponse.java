package br.com.greenvrp.auth;

public record UserResponse(Long id, String nome, String email, Role perfil, boolean ativo) {

	public static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getNome(), user.getEmail(), user.getPerfil(), user.isAtivo());
	}

}
