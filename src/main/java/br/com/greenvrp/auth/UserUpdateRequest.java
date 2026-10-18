package br.com.greenvrp.auth;

import jakarta.validation.constraints.AssertTrue;

public record UserUpdateRequest(Role perfil, Boolean ativo) {

	@AssertTrue(message = "Inform the profile, the status or both")
	public boolean hasAChange() {
		return perfil != null || ativo != null;
	}

}
