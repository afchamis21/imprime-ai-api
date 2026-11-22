package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.PurchasedModel;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchasedModelRepository extends JpaRepository<PurchasedModel, Long> {
    List<PurchasedModel> findAllByBuyerUserIdAndStatusOrderByPurchasedAtDesc(Long buyerUserId, StatusCd status);

    Optional<PurchasedModel> findByGuidAndStatus(String guid, StatusCd status);

    boolean existsByBuyerUserIdAndModel3dIdAndStatus(Long buyerUserId, Long model3dId, StatusCd status);
}
