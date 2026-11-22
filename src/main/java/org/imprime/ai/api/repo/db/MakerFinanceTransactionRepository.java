package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MakerFinanceTransaction;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MakerFinanceTransactionRepository extends JpaRepository<MakerFinanceTransaction, Long> {
    Optional<MakerFinanceTransaction> findByGuidAndStatus(String guid, StatusCd status);

    List<MakerFinanceTransaction> findAllByCompanyIdAndStatusOrderByTransactionDateDescCreateDtDesc(
            Long companyId,
            StatusCd status
    );

    List<MakerFinanceTransaction> findAllByStatusOrderByTransactionDateDescCreateDtDesc(StatusCd status);

    List<MakerFinanceTransaction> findAllByTransactionTypeAndStatusOrderByTransactionDateDescCreateDtDesc(
            FinanceTransactionType transactionType,
            StatusCd status
    );

    Optional<MakerFinanceTransaction> findByIdempotencyKeyAndStatus(String idempotencyKey, StatusCd status);
}
