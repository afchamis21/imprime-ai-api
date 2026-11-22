package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.SupportTicket;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findAllByUserIdAndStatus(Long userId, StatusCd status, Pageable pageable);

    Page<SupportTicket> findAllByUserIdAndTicketStatusAndStatus(Long userId, SupportTicketStatus ticketStatus, StatusCd status, Pageable pageable);

    List<SupportTicket> findAllByStatusOrderByUpdateDtDescCreateDtDesc(StatusCd status);

    Optional<SupportTicket> findByGuidAndStatus(String guid, StatusCd status);

    boolean existsByProtocolCode(String protocolCode);
}
