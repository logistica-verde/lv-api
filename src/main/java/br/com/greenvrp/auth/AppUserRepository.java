package br.com.greenvrp.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByEmail(String email);

	List<AppUser> findAllByOrderByIdAsc();

	long countByPerfilAndAtivoTrue(Role perfil);

}
