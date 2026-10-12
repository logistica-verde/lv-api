package br.com.greenvrp.auth;

import br.com.greenvrp.api.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

	private final AppUserRepository users;

	public UserAdminService(AppUserRepository users) {
		this.users = users;
	}

	@Transactional(readOnly = true)
	public List<AppUser> list() {
		return users.findAllByOrderByIdAsc();
	}

	@Transactional
	public AppUser update(Long id, UserUpdateRequest request) {
		AppUser user = users.findById(id)
			.orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
		Role newRole = request.perfil() != null ? request.perfil() : user.getPerfil();
		boolean newActive = request.ativo() != null ? request.ativo() : user.isAtivo();

		boolean losesAdministration = user.getPerfil() == Role.ADMIN && user.isAtivo()
			&& !(newRole == Role.ADMIN && newActive);
		if (losesAdministration && users.countByPerfilAndAtivoTrue(Role.ADMIN) <= 1) {
			throw new LastAdministratorException();
		}

		user.alterarPerfil(newRole);
		if (newActive) {
			user.ativar();
		}
		else {
			user.desativar();
		}
		return users.save(user);
	}

}
