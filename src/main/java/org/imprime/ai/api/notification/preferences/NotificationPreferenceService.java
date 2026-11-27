package org.imprime.ai.api.notification.preferences;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.User;
import org.springframework.stereotype.Service;

/**
 * Gerencia preferências opcionais de notificação por e-mail (chat digest, avaliações e novidades).
 * E-mails transacionais essenciais (pedidos, pagamentos, reembolsos e segurança) são sempre enviados.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceResponse getMyPreferences() {
        User caller = ServiceContext.getUserOrThrow();
        return repository.findByUserId(caller.getId())
                .map(NotificationPreferenceService::toResponse)
                .orElseGet(() -> new NotificationPreferenceResponse(true, true, true));
    }

    @Transactional
    public NotificationPreferenceResponse updateMyPreferences(UpdateNotificationPreferencesRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        NotificationPreference pref = repository.findByUserId(caller.getId())
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setUserId(caller.getId());
                    created.setChatDigestEnabled(Boolean.TRUE);
                    created.setReviewsEnabled(Boolean.TRUE);
                    created.setMarketingEnabled(Boolean.TRUE);
                    return created;
                });

        if (request != null) {
            if (request.chatDigestEnabled() != null) {
                pref.setChatDigestEnabled(request.chatDigestEnabled());
            }
            if (request.reviewsEnabled() != null) {
                pref.setReviewsEnabled(request.reviewsEnabled());
            }
            if (request.marketingEnabled() != null) {
                pref.setMarketingEnabled(request.marketingEnabled());
            }
        }

        return toResponse(repository.save(pref));
    }

    public boolean isChatDigestEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        return repository.findByUserId(userId)
                .map(pref -> !Boolean.FALSE.equals(pref.getChatDigestEnabled()))
                .orElse(true);
    }

    public boolean isReviewsEnabled(Long userId) {
        if (userId == null) {
            return false;
        }
        return repository.findByUserId(userId)
                .map(pref -> !Boolean.FALSE.equals(pref.getReviewsEnabled()))
                .orElse(true);
    }

    private static NotificationPreferenceResponse toResponse(NotificationPreference pref) {
        return new NotificationPreferenceResponse(
                !Boolean.FALSE.equals(pref.getChatDigestEnabled()),
                !Boolean.FALSE.equals(pref.getReviewsEnabled()),
                !Boolean.FALSE.equals(pref.getMarketingEnabled())
        );
    }
}
