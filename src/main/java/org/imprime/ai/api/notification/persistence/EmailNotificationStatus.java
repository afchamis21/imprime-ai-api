package org.imprime.ai.api.notification.persistence;

public enum EmailNotificationStatus {
    QUEUED,
    SENT,
    FAILED,
    DEAD_LETTERED,
    SKIPPED
}
