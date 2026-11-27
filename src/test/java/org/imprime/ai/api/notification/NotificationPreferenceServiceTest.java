package org.imprime.ai.api.notification;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.notification.preferences.NotificationPreference;
import org.imprime.ai.api.notification.preferences.NotificationPreferenceRepository;
import org.imprime.ai.api.notification.preferences.NotificationPreferenceResponse;
import org.imprime.ai.api.notification.preferences.NotificationPreferenceService;
import org.imprime.ai.api.notification.preferences.UpdateNotificationPreferencesRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    @Mock
    private NotificationPreferenceRepository repository;

    private NotificationPreferenceService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferenceService(repository);
        user = new User();
        user.setId(42L);
        user.setGuid("user-42");
        ServiceContext.getContext().setUser(user);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void returnsAllEnabledByDefaultWhenNoRecordExists() {
        when(repository.findByUserId(42L)).thenReturn(Optional.empty());

        NotificationPreferenceResponse resp = service.getMyPreferences();
        assertTrue(resp.chatDigestEnabled());
        assertTrue(resp.reviewsEnabled());
        assertTrue(resp.marketingEnabled());
    }

    @Test
    void updatesPreferencesLazilyCreatingRecord() {
        when(repository.findByUserId(42L)).thenReturn(Optional.empty());
        when(repository.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferenceResponse updated = service.updateMyPreferences(
                new UpdateNotificationPreferencesRequest(false, null, false)
        );

        assertFalse(updated.chatDigestEnabled());
        assertTrue(updated.reviewsEnabled());
        assertFalse(updated.marketingEnabled());
        verify(repository).save(any(NotificationPreference.class));
    }
}
