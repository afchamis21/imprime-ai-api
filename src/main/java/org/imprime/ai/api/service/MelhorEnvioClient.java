package org.imprime.ai.api.service;

import tools.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.MelhorEnvioProperties;
import org.imprime.ai.api.model.MelhorEnvioCredential;
import org.imprime.ai.api.repo.db.MelhorEnvioCredentialRepository;
import org.imprime.ai.api.util.TokenCipherUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

@Slf4j
@Component
public class MelhorEnvioClient {
    private static final Short CREDENTIAL_ROW_ID = 1;

    private final MelhorEnvioProperties properties;
    private final RestClient restClient;
    private final MelhorEnvioCredentialRepository credentialRepository;
    private final TokenCipherUtil tokenCipherUtil;
    private volatile String accessToken;
    private volatile String refreshToken;

    public MelhorEnvioClient(
            MelhorEnvioProperties properties,
            MelhorEnvioCredentialRepository credentialRepository,
            TokenCipherUtil tokenCipherUtil) {
        this.properties = properties;
        this.credentialRepository = credentialRepository;
        this.tokenCipherUtil = tokenCipherUtil;
        this.accessToken = properties.getAccessToken();
        this.refreshToken = properties.getRefreshToken();
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    @PostConstruct
    void loadRotatedCredentials() {
        credentialRepository.findById(CREDENTIAL_ROW_ID).ifPresent(credential -> {
            accessToken = tokenCipherUtil.decrypt(credential.getEncryptedAccessToken());
            refreshToken = tokenCipherUtil.decrypt(credential.getEncryptedRefreshToken());
        });
    }

    public JsonNode post(String path, Object body) {
        validateConfiguration();
        String usedToken = accessToken;
        try {
            return postWithToken(path, body, usedToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) throw logAndRethrow(path, exception);
            refreshAccessToken(usedToken);
            return postWithToken(path, body, accessToken);
        } catch (RuntimeException exception) {
            throw logAndRethrow(path, exception);
        }
    }

    public JsonNode get(String path) {
        validateConfiguration();
        String usedToken = accessToken;
        try {
            return getWithToken(path, usedToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) throw logAndRethrow(path, exception);
            refreshAccessToken(usedToken);
            return getWithToken(path, accessToken);
        } catch (RuntimeException exception) {
            throw logAndRethrow(path, exception);
        }
    }

    public byte[] download(String absoluteUrl) {
        validateConfiguration();
        URI uri = URI.create(absoluteUrl);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !(host.equals("melhorenvio.com.br") || host.endsWith(".melhorenvio.com.br"))) {
            throw new IllegalArgumentException("URL de etiqueta fora dos domínios permitidos.");
        }
        String usedToken = accessToken;
        try {
            return downloadWithToken(uri, usedToken);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) throw exception;
            refreshAccessToken(usedToken);
            return downloadWithToken(uri, accessToken);
        }
    }

    public void validateConfiguration() {
        if (accessToken == null || accessToken.isBlank()
                || properties.getUserAgent() == null || properties.getUserAgent().isBlank()
                || properties.getBaseUrl() == null || !properties.getBaseUrl().startsWith("https://")) {
            throw new IllegalStateException("Configure MELHOR_ENVIO_BASE_URL, MELHOR_ENVIO_ACCESS_TOKEN e MELHOR_ENVIO_USER_AGENT no backend.");
        }
    }

    private JsonNode postWithToken(String path, Object body, String token) {
        return restClient.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.USER_AGENT, properties.getUserAgent().trim())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode getWithToken(String path, String token) {
        return restClient.get().uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.USER_AGENT, properties.getUserAgent().trim())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
    }

    private byte[] downloadWithToken(URI uri, String token) {
        return RestClient.create().get().uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.USER_AGENT, properties.getUserAgent().trim())
                .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .body(byte[].class);
    }

    private synchronized void refreshAccessToken(String staleToken) {
        if (!Objects.equals(accessToken, staleToken)) return;
        if (refreshToken == null || refreshToken.isBlank()
                || properties.getClientId() == null || properties.getClientId().isBlank()
                || properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            throw new IllegalStateException("Token Melhor Envio expirado; configure client ID, client secret e refresh token no backend.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        if (properties.getRedirectUri() != null && !properties.getRedirectUri().isBlank()) {
            form.add("redirect_uri", properties.getRedirectUri());
        }
        JsonNode tokens = restClient.post().uri("/oauth/token")
                .header(HttpHeaders.USER_AGENT, properties.getUserAgent().trim())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String renewedAccess = tokens == null ? null : tokens.path("access_token").asText(null);
        String renewedRefresh = tokens == null ? null : tokens.path("refresh_token").asText(null);
        if (renewedAccess == null || renewedAccess.isBlank()) {
            throw new IllegalStateException("Melhor Envio não retornou um novo access token.");
        }
        accessToken = renewedAccess;
        if (renewedRefresh != null && !renewedRefresh.isBlank()) refreshToken = renewedRefresh;

        MelhorEnvioCredential credential = credentialRepository.findById(CREDENTIAL_ROW_ID)
                .orElseGet(MelhorEnvioCredential::new);
        credential.setId(CREDENTIAL_ROW_ID);
        credential.setEncryptedAccessToken(tokenCipherUtil.encrypt(accessToken));
        credential.setEncryptedRefreshToken(tokenCipherUtil.encrypt(refreshToken));
        credential.setUpdatedAt(Instant.now());
        credentialRepository.save(credential);
    }

    private RuntimeException logAndRethrow(String path, RuntimeException exception) {
        log.warn("Melhor Envio request failed for path {} ({})", path, exception.getClass().getSimpleName());
        return exception;
    }
}
