package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.RefundRequest;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    Optional<RefundRequest> findByGuidAndStatus(String guid, StatusCd status);

    List<RefundRequest> findAllByStatusOrderByCreateDtDesc(StatusCd status);

    List<RefundRequest> findAllByOrderIdAndStatusOrderByCreateDtDesc(Long orderId, StatusCd status);

    long countByRefundStatusAndStatus(RefundStatus refundStatus, StatusCd status);
}
