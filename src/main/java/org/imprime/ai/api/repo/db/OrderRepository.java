package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findOrderByGuid(String orderGuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findLockedByGuid(String orderGuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(Long orderId);

    Page<Order> findAllByBuyerIdAndStatus(Long buyerId, StatusCd status, Pageable pageable);

    Page<Order> findAllByVendorIdAndStatus(Long vendorId, StatusCd status, Pageable pageable);

    List<Order> findAllByBuyerIdAndStatusOrderByCreateDtDesc(Long buyerId, StatusCd status);

    List<Order> findAllByBuyerIdAndVendorIdAndStatusOrderByCreateDtDesc(Long buyerId, Long vendorId, StatusCd status);

    List<Order> findAllByVendorIdAndStatusOrderByCreateDtDesc(Long vendorId, StatusCd status);

    List<Order> findAllByStatusOrderByCreateDtDesc(StatusCd status);

    List<Order> findAllByCheckoutSessionIdAndBuyerIdAndStatusOrderByIdAsc(
            String checkoutSessionId,
            Long buyerId,
            StatusCd status
    );
}
