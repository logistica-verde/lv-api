package br.com.greenvrp.deliverypoint;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPointRepository extends JpaRepository<DeliveryPoint, Long> {

	List<DeliveryPoint> findByAtivoTrue();

	Optional<DeliveryPoint> findFirstByNomeAndEndereco(String nome, String endereco);

}
