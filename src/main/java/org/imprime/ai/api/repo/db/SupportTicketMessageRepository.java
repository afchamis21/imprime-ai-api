package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.SupportTicketMessage;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {
    List<SupportTicketMessage> findAllBySupportTicketIdAndStatusOrderByCreateDtAsc(Long supportTicketId, StatusCd status);

    long countBySupportTicketIdAndStatus(Long supportTicketId, StatusCd status);
}
