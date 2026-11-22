package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderFileAssetRepository extends JpaRepository<OrderFileAsset, Long> {
    List<OrderFileAsset> findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(Long orderId, StatusCd status);

    List<OrderFileAsset> findAllByOrderIdInAndStatusOrderByPlateNumberAscIdAsc(java.util.Collection<Long> orderIds, StatusCd status);
}
