package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.MessageLkup;
import org.imprime.ai.api.model.enums.MessageCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageLkupRepository extends JpaRepository<MessageLkup, Long> {
    Optional<MessageLkup> findByMessageCd(MessageCd messageCd);
}
