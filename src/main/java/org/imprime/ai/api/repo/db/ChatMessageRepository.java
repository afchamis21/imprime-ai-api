package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.enums.MessageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    Page<ChatMessage> findAllByChatIdOrderByCreateDtDesc(UUID chatId, Pageable pageable);

    List<ChatMessage> findAllByChatIdOrderByCreateDtAsc(UUID chatId);

    List<ChatMessage> findTop20ByChatIdOrderByCreateDtDesc(UUID chatId);

    Optional<ChatMessage> findByGuidAndChatId(String guid, UUID chatId);

    Optional<ChatMessage> findTopByChatIdOrderByCreateDtDesc(UUID chatId);

    Optional<ChatMessage> findTopByChatIdAndTypeOrderByCreateDtDesc(UUID chatId, MessageType type);
}
