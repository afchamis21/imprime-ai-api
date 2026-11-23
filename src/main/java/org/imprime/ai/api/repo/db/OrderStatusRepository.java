package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderStatusRepository extends JpaRepository<OrderStatus, Long> {
    @Query(value = """
    SELECT * FROM ORDER_STATUS os
    WHERE os.ORDER_ID = :orderId
    ORDER BY os.STATUS_DATE DESC, os.ORDER_STATUS_ID DESC
    FETCH NEXT 1 ROWS ONLY
    """, nativeQuery = true)
    Optional<OrderStatus> findLatestStatusByOrderId(Long orderId);

    List<OrderStatus> findAllByOrderIdOrderByStatusDateAsc(Long orderId);

    @Query(value = """
    SELECT DISTINCT ON (os.order_id) os.order_id AS orderId, os.status_cd AS statusCd
    FROM order_status os
    WHERE os.order_id IN (:orderIds)
    ORDER BY os.order_id, os.status_date DESC, os.order_status_id DESC
    """, nativeQuery = true)
    List<LatestOrderStatusView> findLatestStatusesByOrderIds(@Param("orderIds") Collection<Long> orderIds);

    interface LatestOrderStatusView {
        Long getOrderId();

        String getStatusCd();
    }

    @Query(value = """
    SELECT COUNT(*) FROM (
        SELECT DISTINCT ON (os.order_id) os.status_cd
        FROM order_status os
        JOIN orders o ON o.order_id = os.order_id
        WHERE o.status = :activeStatus
        ORDER BY os.order_id, os.status_date DESC, os.order_status_id DESC
    ) latest
    WHERE latest.status_cd = :orderStatus
    """, nativeQuery = true)
    long countOrdersWithLatestStatus(@Param("orderStatus") String orderStatus, @Param("activeStatus") String activeStatus);
}
