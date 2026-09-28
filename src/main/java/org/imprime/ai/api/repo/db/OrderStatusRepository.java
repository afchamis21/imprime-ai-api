package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderStatusRepository extends JpaRepository<OrderStatus, Long> {
    @Query(value = """
    SELECT * FROM ORDER_STATUS os
    WHERE os.ORDER_ID = :orderId
    ORDER BY os.STATUS_DATE DESC
    FETCH NEXT 1 ROWS ONLY
    """, nativeQuery = true)
    Optional<OrderStatus> findLatestStatusByOrderId(Long orderId);
}
