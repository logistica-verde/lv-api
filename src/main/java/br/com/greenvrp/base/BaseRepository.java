package br.com.greenvrp.base;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseRepository extends JpaRepository<Base, Long> {

	List<Base> findByAtivoTrueOrderByNomeAsc();

	List<Base> findAllByOrderByNomeAsc();

}
