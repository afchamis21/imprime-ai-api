package org.imprime.ai.api.notification.preferences;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.response.BaseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/me/notification-preferences")
@JwtAuthenticated
@Tag(name = "Central do Usuário", description = "Preferências de notificações por e-mail")
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferenceService;

    @Operation(summary = "Consultar preferências de e-mail do usuário")
    @GetMapping
    public ResponseEntity<BaseResponse<NotificationPreferenceResponse>> getMyPreferences() {
        return BaseResponse.ok(preferenceService.getMyPreferences());
    }

    @Operation(summary = "Atualizar preferências de e-mail do usuário")
    @PatchMapping
    public ResponseEntity<BaseResponse<NotificationPreferenceResponse>> updateMyPreferences(
            @RequestBody UpdateNotificationPreferencesRequest request) {
        return BaseResponse.ok(preferenceService.updateMyPreferences(request));
    }
}
