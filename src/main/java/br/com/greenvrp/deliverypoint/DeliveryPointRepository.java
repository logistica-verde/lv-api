package br.com.greenvrp.deliverypoint;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPointRepository extends JpaRepository<DeliveryPoint, Long> {

	List<DeliveryPoint> findByAtivoTrue();

}
