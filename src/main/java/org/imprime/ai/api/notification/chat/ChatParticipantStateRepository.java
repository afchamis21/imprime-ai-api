package org.imprime.ai.api.notification.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatParticipantStateRepository extends JpaRepository<ChatParticipantState, ChatParticipantStateId> {

    List<ChatParticipantState> findAllByIdUserGuidAndIdChatIdIn(String userGuid, Collection<UUID> chatIds);

    @Query(value = """
            SELECT *
            FROM chat_participant_state
            WHERE next_notify_at IS NOT NULL
              AND next_notify_at <= :now
            ORDER BY next_notify_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ChatParticipantState> findDuePendingStatesForUpdate(Instant now, int limit);

    @Modifying
    @Query(value = """
            INSERT INTO chat_participant_state (
                chat_id, user_guid, last_read_at, pending_since, next_notify_at, pending_count, updated_at
            )
            VALUES (:chatId, :userGuid, :now, NULL, NULL, 0, :now)
            ON CONFLICT (chat_id, user_guid) DO UPDATE SET
                last_read_at = GREATEST(
                    COALESCE(chat_participant_state.last_read_at, '-infinity'::timestamptz),
                    EXCLUDED.last_read_at
                ),
                pending_since = NULL,
                next_notify_at = NULL,
                pending_count = 0,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsertMarkAsRead(UUID chatId, String userGuid, Instant now);

    @Modifying
    @Query(value = """
            INSERT INTO chat_participant_state (
                chat_id, user_guid, pending_since, next_notify_at, pending_count, updated_at
            )
            VALUES (:chatId, :userGuid, :now, :candidateNotifyAt, 1, :now)
            ON CONFLICT (chat_id, user_guid) DO UPDATE SET
                pending_since = COALESCE(chat_participant_state.pending_since, :now),
                next_notify_at = CASE
                    WHEN chat_participant_state.next_notify_at IS NULL THEN
                        CASE
                            WHEN chat_participant_state.last_email_sent_at IS NULL THEN :candidateNotifyAt
                            ELSE GREATEST(
                                :candidateNotifyAt,
                                chat_participant_state.last_email_sent_at + make_interval(secs => :cooldownSeconds)
                            )
                        END
                    ELSE LEAST(
                        chat_participant_state.next_notify_at,
                        CASE
                            WHEN chat_participant_state.last_email_sent_at IS NULL THEN :candidateNotifyAt
                            ELSE GREATEST(
                                :candidateNotifyAt,
                                chat_participant_state.last_email_sent_at + make_interval(secs => :cooldownSeconds)
                            )
                        END
                    )
                END,
                pending_count = chat_participant_state.pending_count + 1,
                updated_at = :now
            """, nativeQuery = true)
    void upsertSchedulePendingDigest(
            UUID chatId,
            String userGuid,
            Instant now,
            Instant candidateNotifyAt,
            double cooldownSeconds
    );
}
