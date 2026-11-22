package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.OrderShipment;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderShipmentRepository extends JpaRepository<OrderShipment, Long> {
    List<OrderShipment> findAllByOrderIdAndStatusOrderByPackageNumberAscIdAsc(Long orderId, StatusCd status);

    Optional<OrderShipment> findByGuidAndOrderIdAndStatus(String guid, Long orderId, StatusCd status);

    Optional<OrderShipment> findByExternalOrderId(String externalOrderId);
}
