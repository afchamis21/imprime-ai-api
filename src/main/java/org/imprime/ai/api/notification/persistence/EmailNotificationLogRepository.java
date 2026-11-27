package org.imprime.ai.api.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailNotificationLogRepository extends JpaRepository<EmailNotificationLog, Long> {
    Optional<EmailNotificationLog> findByEventId(String eventId);
}
