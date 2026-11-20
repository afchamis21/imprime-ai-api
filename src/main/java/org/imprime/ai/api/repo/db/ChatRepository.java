package org.imprime.ai.api.repo.db;

import jakarta.transaction.Transactional;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.enums.StatusCd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {
    @Query(value = """
            SELECT DISTINCT u.guid FROM chat c
                            JOIN company co ON c.company_guid = co.guid
                            JOIN users u ON (c.user_guid = u.guid OR u.company_id = co.company_id)
            WHERE c.chat_id = :chatId
            """, nativeQuery = true)
    List<String> findAllMemberGuid(UUID chatId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE chat SET last_message_at = now() WHERE chat_id = :chatId", nativeQuery = true)
    void updateLastMessageSentAt(UUID chatId);

    List<Chat> findAllByOrderGuid(String orderGuid);

    List<Chat> findAllByClientGuidAndStatusOrderByLastMessageAtDesc(String clientGuid, StatusCd status);

    List<Chat> findAllByCompanyGuidAndStatusOrderByLastMessageAtDesc(String companyGuid, StatusCd status);

    List<Chat> findAllByClientGuidOrCompanyGuidOrderByLastMessageAtDesc(String clientGuid, String companyGuid);

    boolean existsByOrderGuidAndCompanyGuid(String orderGuid, String companyGuid);

    Optional<Chat> findFirstByOrderGuidAndCompanyGuidAndStatusOrderByCreateDtDesc(
            String orderGuid,
            String companyGuid,
            StatusCd status
    );
}
