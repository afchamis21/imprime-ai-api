package org.imprime.ai.api.repo.db;

import org.imprime.ai.api.model.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMember.Identifier> {
    boolean existsByChatIdAndUserId(UUID chatId, String userId);

    @Query("""
                SELECT cm.userId
                FROM ChatMember cm
                WHERE cm.chatId = :chatId
            """)
    List<String> findUserIdsByChatId(@Param("chatId") UUID chatId);
}
