package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.enums.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
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

    @Query("""
            SELECT count(m) FROM ChatMessage m
            WHERE m.chatId = :chatId
              AND m.type <> org.imprime.ai.api.model.enums.MessageType.SYSTEM
              AND (m.senderId IS NULL OR m.senderId <> :userGuid)
              AND m.createDt > :since
            """)
    long countUnreadSince(UUID chatId, String userGuid, Instant since);

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.chatId = :chatId
              AND m.type <> org.imprime.ai.api.model.enums.MessageType.SYSTEM
              AND (m.senderId IS NULL OR m.senderId <> :userGuid)
              AND m.createDt > :since
            ORDER BY m.createDt ASC
            """)
    List<ChatMessage> findUnreadSince(UUID chatId, String userGuid, Instant since);

    @Query(value = """
            SELECT m.chat_id, COUNT(*)
            FROM chat_message m
            LEFT JOIN chat_participant_state s
              ON s.chat_id = m.chat_id AND s.user_guid = :userGuid
            WHERE m.chat_id IN (:chatIds)
              AND m.type <> 'SYS'
              AND (m.sender_id IS NULL OR m.sender_id <> :userGuid)
              AND m.create_dt > COALESCE(s.last_read_at, to_timestamp(0))
            GROUP BY m.chat_id
            """, nativeQuery = true)
    List<Object[]> countUnreadBatch(Collection<UUID> chatIds, String userGuid);
}
