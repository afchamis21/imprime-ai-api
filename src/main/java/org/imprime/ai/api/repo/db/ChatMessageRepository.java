package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.enums.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    @Query("""
            SELECT count(message) > 0 FROM ChatMessage message
            WHERE message.type = :type AND message.chatId IN
                (SELECT chat.chatId FROM Chat chat WHERE chat.orderGuid = :orderGuid)
            """)
    boolean existsOfferForOrder(String orderGuid, MessageType type);

    Page<ChatMessage> findAllByChatIdOrderByCreateDtDesc(UUID chatId, Pageable pageable);

    List<ChatMessage> findAllByChatIdOrderByCreateDtAsc(UUID chatId);

    List<ChatMessage> findTop20ByChatIdOrderByCreateDtDesc(UUID chatId);

    Optional<ChatMessage> findByGuidAndChatId(String guid, UUID chatId);

    Optional<ChatMessage> findTopByChatIdOrderByCreateDtDesc(UUID chatId);

    Optional<ChatMessage> findTopByChatIdAndTypeOrderByCreateDtDesc(UUID chatId, MessageType type);
}
