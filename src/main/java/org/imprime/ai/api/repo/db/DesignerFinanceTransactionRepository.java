package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.DesignerFinanceTransaction;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DesignerFinanceTransactionRepository extends JpaRepository<DesignerFinanceTransaction, Long> {
    Optional<DesignerFinanceTransaction> findByGuidAndStatus(String guid, StatusCd status);

    List<DesignerFinanceTransaction> findAllByStatusOrderByTransactionDateDescCreateDtDesc(StatusCd status);

    List<DesignerFinanceTransaction> findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
            FinanceTransactionType transactionType,
            StatusCd status
    );

    List<DesignerFinanceTransaction> findAllByDesignerUserIdAndStatusOrderByTransactionDateDescCreateDtDesc(
            Long designerUserId,
            StatusCd status
    );

    long countByTransactionTypeAndTransactionStatusAndStatus(
            FinanceTransactionType transactionType,
            FinanceTransactionStatus transactionStatus,
            StatusCd status
    );
}
