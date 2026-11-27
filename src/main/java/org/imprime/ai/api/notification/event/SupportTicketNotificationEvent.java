package org.imprime.ai.api.notification.event;

public record SupportTicketNotificationEvent(
        String ticketGuid,
        String protocolCode,
        Long userId,
        String subject,
        String category,
        String ticketStatus,
        boolean isDispute,
        String action,
        String replyExcerpt,
        String resolutionNotes
) {
}
